// Circular progress ring, Iron-Man style — used for system & AI status.

export default function RingGauge({ value = 100, size = 64, stroke = 5, label }) {
  const clamped = Math.max(0, Math.min(100, Math.round(value)))
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const offset = circumference * (1 - clamped / 100)

  return (
    <div
      className={`ring-gauge ${clamped < 50 ? 'bad' : ''}`}
      style={{ width: size, height: size }}
      role="img"
      aria-label={`${label ?? clamped + '%'}`}
    >
      <svg width={size} height={size}>
        <circle className="ring-bg" cx={size / 2} cy={size / 2} r={radius} strokeWidth={stroke} />
        <circle
          className="ring-fg"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={stroke}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <span className="ring-label">{label ?? `${clamped}%`}</span>
    </div>
  )
}
