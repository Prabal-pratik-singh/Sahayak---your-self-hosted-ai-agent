// Shared attachment rules + staging, used by BOTH the chat composer and the
// dashboard ask bar. These limits are mirrored server-side in AttachmentService
// (the server re-validates everything — this is only for instant UX feedback).

export const MAX_FILE_BYTES = 15 * 1024 * 1024 // 15 MB
export const MAX_ATTACHMENTS = 5
export const IMAGE_EXTS = ['png', 'jpg', 'jpeg', 'webp', 'gif']
export const DOC_EXTS = ['pdf', 'docx', 'txt', 'md', 'csv']
export const ACCEPT = [...IMAGE_EXTS, ...DOC_EXTS].map((e) => '.' + e).join(',')

export function extOf(name) {
  const dot = name.lastIndexOf('.')
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : ''
}

export function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

let stagedSeq = 0

/**
 * Validates picked/dropped files against the rules and returns the accepted
 * ones as staged attachment objects ({ id, file, name, ext, kind, size,
 * previewUrl }). Rejections are reported through onError(message).
 */
export function stageFiles(fileList, existing, onError) {
  const incoming = Array.from(fileList || [])
  const accepted = []
  let slots = MAX_ATTACHMENTS - existing.length
  for (const file of incoming) {
    if (slots <= 0) {
      onError?.(`You can attach up to ${MAX_ATTACHMENTS} files at a time.`)
      break
    }
    const ext = extOf(file.name)
    const isImage = IMAGE_EXTS.includes(ext)
    const isDoc = DOC_EXTS.includes(ext)
    if (!isImage && !isDoc) {
      onError?.(`"${file.name}" isn't a supported file type.`)
      continue
    }
    if (file.size > MAX_FILE_BYTES) {
      onError?.(`"${file.name}" is larger than 15 MB.`)
      continue
    }
    const dup = (a) => a.name === file.name && a.size === file.size
    if (existing.some(dup) || accepted.some(dup)) continue
    accepted.push({
      id: ++stagedSeq,
      file,
      name: file.name,
      ext,
      kind: isImage ? 'image' : 'doc',
      size: file.size,
      previewUrl: isImage ? URL.createObjectURL(file) : null,
    })
    slots--
  }
  return accepted
}

/** Frees the object URLs of staged attachments that are being discarded. */
export function releaseStaged(staged) {
  for (const a of staged || []) {
    if (a.previewUrl) URL.revokeObjectURL(a.previewUrl)
  }
}
