import { useEffect, useRef } from 'react'
import * as THREE from 'three'

// The dashboard centerpiece: a glowing WebGL network-sphere — a point-cloud
// globe wired with connecting lines, tilted orbit rings, a drifting particle
// field, a rising light beam and a soft radial bloom.
//
// COLOR: everything is tinted from the ONE source of truth — the --accent CSS
// custom property that also themes the rest of the UI (driven by the Settings
// accent picker via data-accent on <html>). Textures are white/greyscale so
// material.color fully controls the hue; on an accent change the whole sphere
// eases to the new colour (no hard cut). Glow strength is nudged by the
// accent's luminance so darker accents (violet, deep blue) still read rich.
//
// Self-contained: owns its renderer, disposes everything on unmount, pauses
// when the tab is hidden, caps DPR, and renders one static (correctly-coloured)
// frame under prefers-reduced-motion.

/** Read the live accent colour from the same CSS variable that themes the UI. */
function readAccent() {
  const raw = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim()
  const color = new THREE.Color(raw || '#38cffb')
  return color
}

/** Perceived luminance (0..1) of a THREE.Color, in sRGB terms. */
function luminance(c) {
  return 0.299 * c.r + 0.587 * c.g + 0.114 * c.b
}

/** A soft round white sprite so points glow as dots; hue comes from material.color. */
function makeDotTexture() {
  const s = 64
  const c = document.createElement('canvas')
  c.width = c.height = s
  const g = c.getContext('2d')
  const grd = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2)
  grd.addColorStop(0, 'rgba(255,255,255,1)')
  grd.addColorStop(0.45, 'rgba(255,255,255,0.5)')
  grd.addColorStop(1, 'rgba(255,255,255,0)')
  g.fillStyle = grd
  g.fillRect(0, 0, s, s)
  const t = new THREE.CanvasTexture(c)
  t.colorSpace = THREE.SRGBColorSpace
  return t
}

/** Vertical white gradient (bright base → fading top) for the light beam. */
function makeBeamTexture() {
  const w = 32
  const h = 256
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  const g = c.getContext('2d')
  const grd = g.createLinearGradient(0, h, 0, 0)
  grd.addColorStop(0, 'rgba(255,255,255,0.95)')
  grd.addColorStop(0.4, 'rgba(255,255,255,0.32)')
  grd.addColorStop(1, 'rgba(255,255,255,0)')
  g.fillStyle = grd
  g.fillRect(0, 0, w, h)
  const side = g.createLinearGradient(0, 0, w, 0)
  side.addColorStop(0, 'rgba(0,0,0,1)')
  side.addColorStop(0.5, 'rgba(0,0,0,0)')
  side.addColorStop(1, 'rgba(0,0,0,1)')
  g.globalCompositeOperation = 'destination-out'
  g.fillStyle = side
  g.fillRect(0, 0, w, h)
  return new THREE.CanvasTexture(c)
}

/** Radial white glow for the bloom sprite; hue comes from material.color. */
function makeGlowTexture() {
  const s = 256
  const c = document.createElement('canvas')
  c.width = c.height = s
  const g = c.getContext('2d')
  const grd = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2)
  grd.addColorStop(0, 'rgba(255,255,255,0.6)')
  grd.addColorStop(0.4, 'rgba(255,255,255,0.22)')
  grd.addColorStop(1, 'rgba(255,255,255,0)')
  g.fillStyle = grd
  g.fillRect(0, 0, s, s)
  return new THREE.CanvasTexture(c)
}

export default function HeroSphere({ accent }) {
  const mountRef = useRef(null)
  const applyAccentRef = useRef(null) // set by the effect below; called on accent change

  useEffect(() => {
    const mount = mountRef.current
    if (!mount) return undefined

    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    let width = mount.clientWidth || 320
    let height = mount.clientHeight || 260

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    renderer.setSize(width, height)
    renderer.setClearColor(0x000000, 0)
    mount.appendChild(renderer.domElement)

    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 100)
    camera.position.set(0, 0, 3.5)

    const disposables = []
    const track = (o) => {
      disposables.push(o)
      return o
    }

    const dotTex = track(makeDotTexture())
    const beamTex = track(makeBeamTexture())
    const glowTex = track(makeGlowTexture())

    // ---- colour state (the single source of truth = --accent) ----
    const WHITE = new THREE.Color(1, 1, 1)
    const cur = readAccent() // base hue, eased
    const curSoft = cur.clone().lerp(WHITE, 0.45) // brighter node cores
    const target = cur.clone()
    const targetSoft = curSoft.clone()
    let curGlow = 1
    let targetGlow = 1

    // ---- radial bloom behind everything ----
    const glowMat = track(new THREE.SpriteMaterial({ map: glowTex, color: cur.clone(), blending: THREE.AdditiveBlending, transparent: true, depthWrite: false, opacity: 0.9 }))
    const glow = new THREE.Sprite(glowMat)
    glow.scale.set(6.5, 6.5, 1)
    glow.position.z = -1.4
    scene.add(glow)

    // ---- light beam rising from the base ----
    const beamMat = track(new THREE.MeshBasicMaterial({ map: beamTex, color: cur.clone(), blending: THREE.AdditiveBlending, transparent: true, depthWrite: false, side: THREE.DoubleSide, opacity: 0.8 }))
    const beam = new THREE.Mesh(track(new THREE.PlaneGeometry(0.7, 3.2)), beamMat)
    beam.position.set(0, 0.5, -0.15)
    scene.add(beam)

    // ---- globe: point cloud + connecting mesh lines ----
    const globe = new THREE.Group()
    scene.add(globe)

    const N = 520
    const golden = Math.PI * (3 - Math.sqrt(5))
    const nodes = []
    for (let i = 0; i < N; i++) {
      const y = 1 - (i / (N - 1)) * 2
      const r = Math.sqrt(Math.max(0, 1 - y * y))
      const theta = golden * i
      nodes.push(new THREE.Vector3(Math.cos(theta) * r, y, Math.sin(theta) * r))
    }
    const nodePos = new Float32Array(N * 3)
    nodes.forEach((p, i) => {
      nodePos[i * 3] = p.x
      nodePos[i * 3 + 1] = p.y
      nodePos[i * 3 + 2] = p.z
    })
    const nodeGeo = track(new THREE.BufferGeometry())
    nodeGeo.setAttribute('position', new THREE.BufferAttribute(nodePos, 3))
    const nodeMat = track(new THREE.PointsMaterial({
      size: 0.055, map: dotTex, color: curSoft.clone(), transparent: true,
      blending: THREE.AdditiveBlending, depthWrite: false, sizeAttenuation: true,
    }))
    globe.add(new THREE.Points(nodeGeo, nodeMat))

    const segs = []
    const TH = 0.30
    const cap = new Int8Array(N)
    for (let i = 0; i < N; i++) {
      for (let j = i + 1; j < N; j++) {
        if (cap[i] >= 3) break
        if (cap[j] >= 3) continue
        if (nodes[i].distanceTo(nodes[j]) < TH) {
          segs.push(nodes[i].x, nodes[i].y, nodes[i].z, nodes[j].x, nodes[j].y, nodes[j].z)
          cap[i]++
          cap[j]++
        }
      }
    }
    const lineGeo = track(new THREE.BufferGeometry())
    lineGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(segs), 3))
    const lineMat = track(new THREE.LineBasicMaterial({ color: cur.clone(), transparent: true, opacity: 0.3, blending: THREE.AdditiveBlending, depthWrite: false }))
    globe.add(new THREE.LineSegments(lineGeo, lineMat))

    // ---- tilted orbit rings ----
    const ringMats = []
    const rings = []
    const ringDefs = [
      { r: 1.5, tilt: [1.35, 0, 0.4], op: 0.5, speed: 0.25 },
      { r: 1.78, tilt: [1.2, 0.5, -0.3], op: 0.34, speed: -0.18 },
      { r: 2.05, tilt: [1.5, -0.4, 0.2], op: 0.22, speed: 0.13 },
    ]
    ringDefs.forEach((d) => {
      const geo = track(new THREE.TorusGeometry(d.r, 0.006, 12, 140))
      const mat = track(new THREE.MeshBasicMaterial({ color: cur.clone(), transparent: true, opacity: d.op, blending: THREE.AdditiveBlending, depthWrite: false }))
      const ring = new THREE.Mesh(geo, mat)
      ring.rotation.set(...d.tilt)
      ring.userData = { speed: d.speed, baseOp: d.op }
      ringMats.push(mat)
      rings.push(ring)
      scene.add(ring)
    })

    // ---- drifting particle field ----
    const P = 170
    const pPos = new Float32Array(P * 3)
    for (let i = 0; i < P; i++) {
      const rr = 1.4 + Math.random() * 1.3
      const th = Math.random() * Math.PI * 2
      const ph = Math.acos(2 * Math.random() - 1)
      pPos[i * 3] = rr * Math.sin(ph) * Math.cos(th)
      pPos[i * 3 + 1] = rr * Math.cos(ph)
      pPos[i * 3 + 2] = rr * Math.sin(ph) * Math.sin(th)
    }
    const pGeo = track(new THREE.BufferGeometry())
    pGeo.setAttribute('position', new THREE.BufferAttribute(pPos, 3))
    const pMat = track(new THREE.PointsMaterial({ size: 0.03, map: dotTex, color: cur.clone(), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, sizeAttenuation: true, opacity: 0.8 }))
    const particles = new THREE.Points(pGeo, pMat)
    scene.add(particles)

    globe.rotation.z = 0.18

    // recompute the eased targets from the live --accent value
    const refreshTarget = () => {
      const a = readAccent()
      target.copy(a)
      targetSoft.copy(a).lerp(WHITE, 0.45)
      // darker accents get a little more bloom so they read as rich, not dim
      targetGlow = THREE.MathUtils.clamp(1 + (0.5 - luminance(a)) * 0.9, 0.8, 1.6)
    }
    refreshTarget()
    curGlow = targetGlow

    // apply the CURRENT (eased) colour to every material
    const paint = () => {
      lineMat.color.copy(cur)
      ringMats.forEach((m) => m.color.copy(cur))
      beamMat.color.copy(cur)
      pMat.color.copy(cur)
      glowMat.color.copy(cur)
      nodeMat.color.copy(curSoft)
    }
    paint()

    // exposed so the accent-change effect can retarget without rebuilding the scene
    applyAccentRef.current = () => {
      refreshTarget()
      if (reduceMotion) {
        cur.copy(target)
        curSoft.copy(targetSoft)
        curGlow = targetGlow
        paint()
        renderer.render(scene, camera)
      }
    }

    const clock = new THREE.Clock()
    let raf = 0

    const renderFrame = () => {
      const t = clock.getElapsedTime()
      // ease colour toward target (~0.5s to settle at 60fps)
      cur.lerp(target, 0.08)
      curSoft.lerp(targetSoft, 0.08)
      curGlow += (targetGlow - curGlow) * 0.08
      paint()

      globe.rotation.y += 0.0016 + 0.0004 * Math.sin(t * 0.3)
      globe.rotation.x = 0.1 * Math.sin(t * 0.2)
      rings.forEach((ring, i) => {
        ring.rotation.z += ring.userData.speed * 0.01
        ring.rotation.y += ring.userData.speed * 0.004 * (i + 1)
      })
      particles.rotation.y -= 0.0006
      particles.rotation.x = 0.05 * Math.sin(t * 0.15)
      beamMat.opacity = (0.72 + 0.12 * Math.sin(t * 1.6)) * curGlow
      glowMat.opacity = (0.8 + 0.12 * Math.sin(t * 1.1)) * curGlow
      renderer.render(scene, camera)
    }

    const loop = () => {
      renderFrame()
      raf = requestAnimationFrame(loop)
    }

    if (reduceMotion) {
      paint()
      renderer.render(scene, camera)
    } else {
      loop()
    }

    const onVisibility = () => {
      if (document.hidden) {
        cancelAnimationFrame(raf)
        raf = 0
      } else if (!reduceMotion && !raf) {
        clock.getDelta()
        loop()
      }
    }
    document.addEventListener('visibilitychange', onVisibility)

    const ro = new ResizeObserver(() => {
      width = mount.clientWidth || width
      height = mount.clientHeight || height
      camera.aspect = width / height
      camera.updateProjectionMatrix()
      renderer.setSize(width, height)
      if (raf === 0 && !document.hidden) renderFrame()
    })
    ro.observe(mount)

    return () => {
      cancelAnimationFrame(raf)
      applyAccentRef.current = null
      document.removeEventListener('visibilitychange', onVisibility)
      ro.disconnect()
      disposables.forEach((d) => d.dispose?.())
      renderer.dispose()
      renderer.forceContextLoss?.()
      if (renderer.domElement.parentNode === mount) mount.removeChild(renderer.domElement)
    }
  }, [])

  // Live recolour when the accent changes — no scene rebuild, no reload. rAF so
  // the new data-accent has been applied to <html> before we read --accent.
  useEffect(() => {
    const id = requestAnimationFrame(() => applyAccentRef.current?.())
    return () => cancelAnimationFrame(id)
  }, [accent])

  return <div className="hero-sphere" ref={mountRef} aria-hidden="true" />
}
