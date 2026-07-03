// One tiny inline icon set so the whole app shares a consistent stroke style.

const base = {
  width: 18,
  height: 18,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
}

export const HomeIcon = () => (
  <svg {...base}><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V21h14V9.5" /></svg>
)
export const ChatIcon = () => (
  <svg {...base}><path d="M21 12a8 8 0 0 1-8 8H4l2.2-2.6A8 8 0 1 1 21 12Z" /></svg>
)
export const TasksIcon = () => (
  <svg {...base}><rect x="3" y="4" width="18" height="16" rx="3" /><path d="m8 12 2.5 2.5L16 9" /></svg>
)
export const CalendarIcon = () => (
  <svg {...base}><rect x="3" y="5" width="18" height="16" rx="3" /><path d="M8 3v4M16 3v4M3 10h18" /></svg>
)
export const ActivityIcon = () => (
  <svg {...base}><path d="M3 12h4l3-8 4 16 3-8h4" /></svg>
)
export const PlugIcon = () => (
  <svg {...base}><path d="M9 7V3M15 7V3" /><path d="M6 7h12v4a6 6 0 0 1-6 6v0a6 6 0 0 1-6-6V7Z" /><path d="M12 17v4" /></svg>
)
export const GridIcon = () => (
  <svg {...base}><rect x="3" y="3" width="7" height="7" rx="2" /><rect x="14" y="3" width="7" height="7" rx="2" /><rect x="3" y="14" width="7" height="7" rx="2" /><rect x="14" y="14" width="7" height="7" rx="2" /></svg>
)
export const GearIcon = () => (
  <svg {...base}><circle cx="12" cy="12" r="3.2" /><path d="M19 12a7 7 0 0 0-.14-1.4l2-1.55-2-3.46-2.37.95a7 7 0 0 0-2.42-1.4L13.7 2h-3.4l-.37 2.14a7 7 0 0 0-2.42 1.4l-2.37-.95-2 3.46 2 1.55a7.1 7.1 0 0 0 0 2.8l-2 1.55 2 3.46 2.37-.95a7 7 0 0 0 2.42 1.4L10.3 22h3.4l.37-2.14a7 7 0 0 0 2.42-1.4l2.37.95 2-3.46-2-1.55c.09-.45.14-.92.14-1.4Z" /></svg>
)
export const SearchIcon = () => (
  <svg {...base}><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
)
export const BellIcon = () => (
  <svg {...base}><path d="M18 9a6 6 0 1 0-12 0c0 6-2 7-2 7h16s-2-1-2-7" /><path d="M10.5 20a2 2 0 0 0 3 0" /></svg>
)
export const SunIcon = () => (
  <svg {...base}><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
)
export const MoonIcon = () => (
  <svg {...base}><path d="M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8Z" /></svg>
)
export const MicIcon = () => (
  <svg {...base}><rect x="9" y="2" width="6" height="12" rx="3" /><path d="M5 10v1a7 7 0 0 0 14 0v-1" /><path d="M12 18v4" /></svg>
)
export const SpeakerIcon = ({ on = true }) => (
  <svg {...base}>
    <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" fill="currentColor" stroke="none" />
    {on ? (
      <>
        <path d="M15.5 8.5a5 5 0 0 1 0 7" />
        <path d="M18.5 5.5a9 9 0 0 1 0 13" />
      </>
    ) : (
      <>
        <path d="m16 9 6 6" />
        <path d="m22 9-6 6" />
      </>
    )}
  </svg>
)
export const SendIcon = () => (
  <svg {...base}><path d="m3 11 18-8-8 18-2.5-7.5L3 11Z" /></svg>
)
export const PlusIcon = () => (
  <svg {...base}><path d="M12 5v14M5 12h14" /></svg>
)
export const XIcon = () => (
  <svg {...base}><path d="M6 6l12 12M18 6 6 18" /></svg>
)
export const PinIcon = ({ filled = false }) => (
  <svg {...base} fill={filled ? 'currentColor' : 'none'}>
    <path d="M12 17v5M7 4h10l-1.5 6.5L18 14H6l2.5-3.5L7 4Z" />
  </svg>
)
export const TrashIcon = () => (
  <svg {...base}><path d="M4 7h16M10 11v6M14 11v6" /><path d="M6 7l1 13h10l1-13" /><path d="M9 7V4h6v3" /></svg>
)
export const CopyIcon = () => (
  <svg {...base}><rect x="9" y="9" width="12" height="12" rx="2" /><path d="M5 15V5a2 2 0 0 1 2-2h10" /></svg>
)
export const LogoutIcon = () => (
  <svg {...base}><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="m16 17 5-5-5-5" /><path d="M21 12H9" /></svg>
)
export const MenuIcon = () => (
  <svg {...base}><path d="M3 6h18M3 12h18M3 18h18" /></svg>
)
export const CheckIcon = () => (
  <svg {...base}><path d="m4 12.5 5 5L20 6.5" /></svg>
)
export const RestartIcon = () => (
  <svg {...base}><path d="M3 12a9 9 0 1 0 3-6.7" /><path d="M3 4v5h5" /></svg>
)
export const SparkIcon = () => (
  <svg {...base}><path d="M12 2l1.8 6.2L20 10l-6.2 1.8L12 18l-1.8-6.2L4 10l6.2-1.8L12 2Z" /></svg>
)
