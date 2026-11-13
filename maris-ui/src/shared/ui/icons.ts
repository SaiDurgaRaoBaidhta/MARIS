// Line icons and the caption -> icon rules, copied from maris-2-v4.html (MARIS v2 shell).
export const ICON: Record<string, string> = {
  home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
  grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  map: '<path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2z"/><path d="M9 4v14M15 6v14"/>',
  print: '<path d="M6 9V3h12v6"/><rect x="3" y="9" width="18" height="8" rx="2"/><path d="M6 14h12v7H6z"/>',
  chart: '<path d="M4 20V10M10 20V4M16 20v-7M2 20h20"/>',
  back: '<path d="M15 18l-6-6 6-6"/>',
  chev: '<path d="M9 6l6 6-6 6"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/>',
  lock: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
  train: '<rect x="5" y="3" width="14" height="14" rx="3"/><path d="M5 11h14M8 21l2-4M16 21l-2-4"/>',
  alert: '<path d="M12 3 2 21h20L12 3z"/><path d="M12 10v5M12 18h.01"/>',
  arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
  wrench: '<path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4z"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  signal: '<path d="M5 12a7 7 0 0 1 14 0M8.5 12a3.5 3.5 0 0 1 7 0"/><path d="M12 12v9"/>',
  card: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>',
  building: '<path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6"/>',
  radar: '<circle cx="12" cy="12" r="9"/><path d="M12 12l6-6M12 3v3M21 12h-3"/>',
  table: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 10h18M9 4v16"/>',
  db: '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>',
  sliders: '<path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6"/>',
  activity: '<path d="M22 12h-4l-3 9L9 3l-3 9H2"/>',
  tower: '<path d="M12 2v20M8 22l4-14 4 14M5 8h14M7 5h10"/>',
  bus: '<rect x="4" y="3" width="16" height="15" rx="3"/><path d="M4 11h16M8 21v-3M16 21v-3"/>',
  cpu: '<rect x="6" y="6" width="12" height="12" rx="2"/><path d="M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4"/>',
  bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10 21h4"/>',
  file: '<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><path d="M14 3v6h6"/>',
  msg: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  x: '<path d="M6 6l12 12M18 6 6 18"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
  drop: '<path d="M12 3s6 7 6 11a6 6 0 0 1-12 0c0-4 6-11 6-11z"/>',
  link: '<path d="M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1"/>',
  edit: '<path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
  split: '<path d="M16 3h5v5M8 3H3v5M21 3l-7 7M3 3l7 7M12 14v7"/>',
  swap: '<path d="M7 7h13l-4-4M17 17H4l4 4"/>',
  gauge: '<path d="M12 14l4-4M4 18a9 9 0 1 1 16 0"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h10"/>'
};
// more icons for menu functions (24px line icons)
Object.assign(ICON, {
  trend: '<path d="M3 17l6-6 4 4 8-8"/><path d="M14 7h7v7"/>', pie: '<path d="M21 12A9 9 0 1 1 12 3v9z"/><path d="M15 3.5A9 9 0 0 1 20.5 9H15z"/>',
  bars: '<rect x="4" y="10" width="4" height="10" rx="1"/><rect x="10" y="4" width="4" height="16" rx="1"/><rect x="16" y="13" width="4" height="7" rx="1"/>',
  history: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5M12 7v5l3 2"/>', timer: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2 2M9 2h6"/>',
  hourglass: '<path d="M6 2h12M6 22h12M7 2c0 5 10 5 10 10S7 17 7 22M17 2c0 5-10 5-10 10s10 5 10 10"/>', check: '<path d="M20 6 9 17l-5-5"/>',
  clip: '<rect x="6" y="4" width="12" height="17" rx="2"/><path d="M9 4V3h6v1M9 10h6M9 14h6M9 18h3"/>', repeat: '<path d="M17 2l4 4-4 4"/><path d="M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4"/><path d="M21 13v2a3 3 0 0 1-3 3H3"/>',
  zap: '<path d="M13 2 3 14h9l-1 8 10-12h-9z"/>', shield: '<path d="M12 2l8 4v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6z"/>', home2: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9v12h14V9M10 21v-5h4v5"/>',
  pin: '<path d="M12 22s7-7 7-12a7 7 0 1 0-14 0c0 5 7 12 7 12z"/><circle cx="12" cy="10" r="2.5"/>', flag: '<path d="M4 22V4M4 4h12l-2 4 2 4H4"/>',
  target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>', layers: '<path d="m12 3 9 5-9 5-9-5z"/><path d="m3 13 9 5 9-5"/>',
  box: '<path d="m12 3 8 4.5v9L12 21l-8-4.5v-9z"/><path d="m4 7.5 8 4.5 8-4.5M12 12v9"/>', tag: '<path d="M20 12 12 20 3 11V4h7z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
  sparkle: '<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6"/>', brush: '<path d="M18 2l4 4-9 9-4-4z"/><path d="M9 11c-3 0-5 2-5 5 0 2-2 3-2 3s5 1 7-1c2-1.5 2-4 2-4"/>',
  spray: '<path d="M8 10h8v11H8z"/><path d="M10 10V6h4v4M14 4h2M17 3l1-1M17 6h2M18 8l1 1"/>', mic: '<rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v4"/>',
  phone: '<path d="M22 16.9v3a2 2 0 0 1-2.2 2A19.8 19.8 0 0 1 2 4.2 2 2 0 0 1 4 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.2-1.2a2 2 0 0 1 2.1-.5c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z"/>',
  send: '<path d="M22 2 11 13M22 2l-7 20-4-9-9-4z"/>', inbox: '<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.5 5h13L22 12v7H2v-7z"/>',
  link2: '<path d="M9 17H7a5 5 0 0 1 0-10h2M15 7h2a5 5 0 0 1 0 10h-2M8 12h8"/>', unlink: '<path d="M9 17H7a5 5 0 0 1 0-10h2M15 7h2a5 5 0 0 1 0 10h-2"/>',
  plus: '<circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/>', minusc: '<circle cx="12" cy="12" r="9"/><path d="M8 12h8"/>', xc: '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6M9 9l6 6"/>',
  ban: '<circle cx="12" cy="12" r="9"/><path d="m5.6 5.6 12.8 12.8"/>', key: '<circle cx="7.5" cy="15.5" r="4.5"/><path d="M10.7 12.3 21 2M16 7l3 3M18 5l2 2"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
  nut: '<path d="M12 2l8.7 5v10L12 22l-8.7-5V7z"/><circle cx="12" cy="12" r="3"/>', disc: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="2"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3"/>',
  wheel: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="3"/><path d="M12 3v6M12 15v6M3 12h6M15 12h6"/>', hammer: '<path d="m15 12-8.5 8.5a2.1 2.1 0 0 1-3-3L12 9"/><path d="M17.6 15 22 10.6 14 2.6 9.6 7z"/>',
  ruler: '<path d="M21.3 8.7 8.7 21.3a1 1 0 0 1-1.4 0l-4.6-4.6a1 1 0 0 1 0-1.4L15.3 2.7a1 1 0 0 1 1.4 0l4.6 4.6a1 1 0 0 1 0 1.4z"/><path d="m7.5 10.5 2 2M10.5 7.5l2 2M13.5 4.5l2 2M4.5 13.5l2 2"/>',
  scan: '<path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M7 12h10"/>', search2: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4M8 11h6M11 8v6"/>',
  eye: '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>', book: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5z"/><path d="M4 19.5A2.5 2.5 0 0 0 6.5 22H20v-5"/>',
  folder: '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>', archive: '<rect x="2" y="3" width="20" height="5" rx="1"/><path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8M10 12h4"/>',
  sheet: '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M3 15h18M9 3v18M15 3v18"/>', grid9: '<rect x="3" y="3" width="5" height="5"/><rect x="10" y="3" width="5" height="5"/><rect x="17" y="3" width="4" height="5"/><rect x="3" y="10" width="5" height="5"/><rect x="10" y="10" width="5" height="5"/><rect x="17" y="10" width="4" height="5"/>',
  route: '<circle cx="6" cy="19" r="3"/><circle cx="18" cy="5" r="3"/><path d="M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15"/>', compass: '<circle cx="12" cy="12" r="9"/><path d="m16 8-2 6-6 2 2-6z"/>',
  flame: '<path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1.1-2.1-.2-4 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3.3.5 1.5 1.5 2.8 2.5 2.8z"/>',
  thermo: '<path d="M14 14.8V4a2 2 0 1 0-4 0v10.8a4 4 0 1 0 4 0z"/>', battery: '<rect x="2" y="7" width="17" height="10" rx="2"/><path d="M22 11v2M6 11v2M10 11v2"/>', plug: '<path d="M9 2v6M15 2v6M6 8h12v4a6 6 0 0 1-12 0zM12 18v4"/>',
  star: '<path d="m12 2 3 6.5 7 .8-5.2 4.8 1.4 7-6.2-3.6-6.2 3.6 1.4-7L2 9.3l7-.8z"/>', award: '<circle cx="12" cy="8" r="6"/><path d="M8.2 13 7 22l5-3 5 3-1.2-9"/>', users: '<circle cx="9" cy="8" r="4"/><path d="M2 21c0-4 3-6 7-6s7 2 7 6M16 4a4 4 0 0 1 0 8M22 21c0-3-2-5-4-5.5"/>',
  lockopen: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 7.8-1"/>', download: '<path d="M12 3v12M7 10l5 5 5-5M4 21h16"/>', upload: '<path d="M12 21V9M7 14l5-5 5 5M4 3h16"/>',
  refresh: '<path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/>', filter: '<path d="M3 4h18l-7 8v6l-4 2v-8z"/>', sort: '<path d="M7 4v16M3 16l4 4 4-4M17 20V4M13 8l4-4 4 4"/>', hash: '<path d="M4 9h16M4 15h16M10 3 8 21M16 3l-2 18"/>',
  percent: '<path d="M19 5 5 19"/><circle cx="6.5" cy="6.5" r="2.5"/><circle cx="17.5" cy="17.5" r="2.5"/>', dollar: '<path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>',
  alarm: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2 2M5 3 2 6M22 6l-3-3"/>', sunrise: '<path d="M12 2v6M4.2 10.2l1.4 1.4M2 18h20M18.4 11.6l1.4-1.4M16 18a4 4 0 0 0-8 0M8 6l4-4 4 4"/>', moon2: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>',
  cloud: '<path d="M17.5 19H9a7 7 0 1 1 6.7-9h1.8a4.5 4.5 0 1 1 0 9z"/>', crane: '<path d="M4 21V3h10M4 7h16M14 3v4M20 7v6M18 13h4M10 21h-6"/>', container: '<rect x="2" y="6" width="20" height="12" rx="1"/><path d="M6 6v12M10 6v12M14 6v12M18 6v12"/>',
  bolt: '<circle cx="12" cy="12" r="9"/><path d="M13 7 9 13h4l-1 4 4-6h-4z"/>', siren: '<path d="M7 18v-6a5 5 0 0 1 10 0v6M5 21h14M12 3v2M4.2 7.2 5.6 8.6M19.8 7.2l-1.4 1.4"/>', bug: '<rect x="8" y="6" width="8" height="14" rx="4"/><path d="M19 13h3M2 13h3M19 7l2-2M3 5l2 2M19 19l2 2M3 21l2-2M12 6V2"/>'
});
// icons for functions that had no fitting one
Object.assign(ICON, {
  login: '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3"/>',
  logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
  comment: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/><path d="M8 9h8M8 13h5"/>',
  trash: '<path d="M3 6h18M8 6V4h8v2M6 6l1 14a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-14M10 11v6M14 11v6"/>',
  lockc: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
  rails: '<path d="M7 2v20M17 2v20M7 6h10M7 12h10M7 18h10"/>',
  // clearer pictures for the applications and a few functions
  headset: '<path d="M3 14v-2a9 9 0 0 1 18 0v2"/><path d="M21 15a2 2 0 0 1-2 2h-1v-6h1a2 2 0 0 1 2 2zM3 15a2 2 0 0 0 2 2h1v-6H5a2 2 0 0 0-2 2z"/><path d="M18 17v1a3 3 0 0 1-3 3h-3"/>',
  factory: '<path d="M2 20V10l6 4v-4l6 4V5h7v15z"/><path d="M6 17h2M11 17h2M16 17h2M2 20h20"/>',
  ctower: '<path d="M6 7h12l-2 5H8z"/><path d="M9 12 8 21M15 12l1 9M5 21h14M12 7V3M10 3h4"/>',
  monitor: '<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/><path d="m6 13 3-4 3 3 3-5 3 6"/>',
  trainfront: '<rect x="5" y="3" width="14" height="14" rx="3"/><path d="M5 10h14M12 3v7M8 21l2-4M16 21l-2-4"/><path d="M8.5 13.5h.01M15.5 13.5h.01"/>',
  hardhat: '<path d="M2 18a1 1 0 0 0 1 1h18a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1H3a1 1 0 0 0-1 1z"/><path d="M10 15V6.5a2 2 0 0 1 4 0V15"/><path d="M4 15v-3a6 6 0 0 1 6-6M14 6a6 6 0 0 1 6 6v3"/>',
  supervisor: '<circle cx="10" cy="8" r="4"/><path d="M2 21c0-4 3.5-6 8-6 1.6 0 3 .3 4.2.8"/><path d="m15 19 2 2 4-4"/>',
  clipcheck: '<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V3h6v1M9 13l2 2 4-4"/>',
  calcheck: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4M9 15l2 2 4-4"/>',
  scale: '<path d="M12 3v18M7 21h10M4 7h16M4 7l-2.5 6a2.5 2.5 0 0 0 5 0zM20 7l-2.5 6a2.5 2.5 0 0 0 5 0z"/>',
  radar: '<path d="M19.1 4.9A10 10 0 1 0 22 12"/><path d="M16.2 7.8A6 6 0 1 0 18 12"/><path d="m12 12 7-7"/><circle cx="12" cy="12" r="1.5"/>'
});
// icons for the members of one family (wash, incidents, changes …) so a page never shows the same picture twice
Object.assign(ICON, {
  washdue: '<path d="M10 3S4.5 9 4.5 13.5A5.5 5.5 0 0 0 11 19"/><circle cx="17" cy="16" r="5"/><path d="M17 13.5V16l1.6 1.4"/>',
  washrpt: '<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V3h6v1"/><path d="M12 9s-3 3.2-3 5.2a3 3 0 0 0 6 0C15 12.2 12 9 12 9z"/>',
  washok: '<path d="M12 2.7S6 9 6 14a6 6 0 0 0 12 0c0-5-6-11.3-6-11.3z"/><path d="m9.3 14.2 1.9 1.9 3.6-3.6"/>',
  washdummy: '<path d="M12 2.7S6 9 6 14a6 6 0 0 0 12 0c0-5-6-11.3-6-11.3z" stroke-dasharray="2.6 2.4"/>',
  alertc: '<circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.5h.01"/>',
  trainalert: '<rect x="3" y="3" width="12" height="13" rx="3"/><path d="M3 9.5h12M6 20l1.5-4M12 20l-1.5-4M20 6v6M20 15.5h.01"/>',
  clipalert: '<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V3h6v1M12 9v5M12 17h.01"/>',
  eraser: '<path d="m7 21-4.3-4.3a1 1 0 0 1 0-1.4l9.6-9.6a1 1 0 0 1 1.4 0l5.6 5.6a1 1 0 0 1 0 1.4L11 21z"/><path d="M22 21H7M5 11l9 9"/>',
  toggle: '<rect x="2" y="7" width="20" height="10" rx="5"/><circle cx="16" cy="12" r="3"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'
});
// a small icon for a menu function: every rule whose words are in the caption adds its icons, best first;
// on a page each function takes the first of its icons that no other function there has taken.
export const ITEM_ICON: [RegExp, string[]][] = [
  [/sub-?menu|menu$/i, ["menu", "layers", "folder", "list", "grid9", "book"]], [/shop map/i, ["building", "factory", "crane", "container"]], [/station map/i, ["pin", "flag"]],
  [/system wide/i, ["radar"]], [/yard map|map$/i, ["map", "compass", "route", "target", "flag", "pin"]], [/storage track/i, ["rails", "container", "trainfront", "train", "box"]],
  [/yard tracking|shop \/ yard/i, ["route", "map"]],
  [/interior.*clean/i, ["spray"]], [/clean input/i, ["sparkle"]], [/thorough|clean/i, ["brush", "sparkle", "spray"]],
  [/wash overdue/i, ["washdue"]], [/wash report/i, ["washrpt"]], [/dummy.*wash/i, ["washdummy"]], [/wash complete/i, ["washok", "drop"]], [/wash/i, ["drop", "washok"]],
  [/message|msg/i, ["msg"]], [/intercom/i, ["mic"]],
  [/split/i, ["split"]], [/swap/i, ["swap"]], [/combine/i, ["link"]], [/make train/i, ["plus"]], [/clear train/i, ["xc"]],
  [/reconfigure/i, ["refresh", "layers"]], [/lay ?up/i, ["moon2", "pin"]], [/pseudo/i, ["layers", "box"]],
  [/fix .*orientation/i, ["compass", "edit", "refresh"]], [/orientation/i, ["compass", "refresh"]], [/force clear/i, ["eraser", "ban"]], [/force close/i, ["lockc"]],
  [/symptom/i, ["bug"]], [/status train/i, ["train", "trainfront"]],
  [/history/i, ["history", "archive", "book", "clock", "clip"]],
  [/change incident.*never|never go/i, ["ban", "xc"]], [/change incident.*status|change .*status/i, ["toggle", "flag"]], [/change incident/i, ["alertc", "edit"]],
  [/change yard/i, ["route", "pin"]], [/change config/i, ["sliders", "gear"]], [/change consist/i, ["edit", "swap"]],
  [/length log/i, ["ruler", "train"]], [/dist log|availab/i, ["check", "pie", "percent"]], [/incident log/i, ["clip", "book"]], [/consist log|\blog\b/i, ["book", "clip"]],
  [/nogo|no go/i, ["ban", "xc"]], [/revenue.*fix/i, ["refresh", "dollar"]], [/revenue/i, ["dollar"]], [/incident number/i, ["hash"]],
  [/repair vehicle event/i, ["hammer", "calendar"]], [/repair detail/i, ["search2", "wrench"]], [/incd rep|rep maint/i, ["gear", "nut", "wrench"]], [/repair/i, ["wrench", "hammer", "nut", "gear"]],
  [/event/i, ["calendar", "flag"]], [/train incident/i, ["trainalert", "alert"]], [/incident shop|incd shop/i, ["building", "alert"]], [/incident misc/i, ["alertc", "alert"]],
  [/incd maint|incident maint/i, ["clipalert", "alert"]], [/incident yard/i, ["alert", "pin"]], [/incident|incd/i, ["alert", "alertc", "clipalert", "siren"]],
  [/in the door/i, ["login"]], [/out the door/i, ["logout"]], [/labor detail/i, ["user"]], [/labor/i, ["users", "user"]], [/comment/i, ["comment"]],
  [/inquiry stock|stock inquiry/i, ["search2"]], [/stock/i, ["archive"]], [/kit/i, ["box"]], [/scrap|purge/i, ["trash"]],
  [/sub job/i, ["list"]], [/reprint|print/i, ["print"]], [/d & c|sheet/i, ["sheet"]], [/status job/i, ["check", "eye"]], [/job/i, ["clip"]],
  [/traction motor/i, ["zap"]], [/axle/i, ["wheel"]], [/truck build/i, ["hammer"]], [/rebuil|rebuild/i, ["refresh", "hammer", "nut"]], [/survey/i, ["scan", "clip"]],
  [/serial/i, ["hash", "scan"]], [/batch/i, ["upload"]], [/component to car/i, ["plus", "plug"]], [/smp|add\/remove|add config/i, ["plug", "plus"]],
  [/config.*inquiry|config.*except/i, ["search2", "filter"]], [/config|cmpnt|component/i, ["cpu", "grid9", "sliders"]],
  [/scram/i, ["scale"]], [/repeater/i, ["repeat"]], [/test hours/i, ["gauge", "timer"]], [/summary/i, ["hourglass", "timer"]], [/hours|hrs\b/i, ["timer", "clock", "hourglass", "alarm"]],
  [/pm sched|schedul/i, ["alarm", "calendar"]], [/\bpm\b/i, ["gauge"]], [/daily/i, ["calcheck"]], [/home yard fleet/i, ["home2", "home"]], [/home yard/i, ["home", "home2"]],
  [/yard status/i, ["pin"]], [/mod track|parts\/mod/i, ["tag", "box"]], [/parts|mods?\b|awp/i, ["box", "tag", "layers"]],
  [/status vehicle|vehicle status|^status/i, ["eye", "check"]], [/fleet status/i, ["activity", "bars"]], [/fleet/i, ["bars", "grid9"]],
  [/sas|ezetrieve|wayside/i, ["pie", "percent"]], [/central manager/i, ["users"]], [/audit/i, ["shield"]],
  [/add new car/i, ["plus"]], [/edit car/i, ["edit"]], [/inquiry|record|look ?up|search/i, ["search2", "eye"]],
  [/report|rpt|queries/i, ["chart", "trend", "bars", "pie", "sheet"]], [/maint/i, ["sliders", "gear", "nut"]], [/table|list|index/i, ["list", "sheet"]], [/user|employee/i, ["user", "users"]],
  [/update|change|edit/i, ["edit"]], [/\badd\b/i, ["plus"]], [/vehicle|car\b|cars\b/i, ["bus", "train"]]];
// the theme switch: sun in the dark theme, moon in the light one (as the reference header)
ICON.sun = '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>';

export const SECTION_ICON: Record<string, string> = {
  "Vehicle Trouble Desk": "headset", "Vehicle Shops": "factory", "Towers": "ctower", "Central Control": "monitor", "Trans. Mgmt": "trainfront",
  "Main Line Techs": "hardhat", "Second Repair": "wrench", "Vehicle Shop Supr": "supervisor", "Shop Scheduler": "calendar",
  "Vehicle Maint. Admin.": "clipcheck", "Vehicle Maint. Engin.": "gear", "Reliab. Engin.": "activity", "DB Maint.": "db", "Table Maint.": "table",
  "Mgmt Reports": "chart", "General User": "user", "Way Side": "signal", "AFC Entry": "card", "AFC Mgmt": "card"
};

/** Icon for a menu function: every rule whose words are in the caption adds its icons, best first;
 *  on one page each function takes the first of its icons that no other function there has taken. */
export function iconFor(caption: string, used?: Map<string, string>): string {
  const cand: string[] = [];
  ITEM_ICON.forEach(([re, l]) => { if (re.test(caption)) l.forEach(n => { if (!cand.includes(n)) cand.push(n); }); });
  if (!cand.length) cand.push("file");
  if (!used) return cand[0];
  const key = "#" + caption.toLowerCase().replace(/\s+/g, " ").trim();
  const had = used.get(key);
  if (had) return had;
  const pick = cand.find(n => !used.has(n)) || cand[0];
  used.set(pick, key); used.set(key, pick);
  return pick;
}
