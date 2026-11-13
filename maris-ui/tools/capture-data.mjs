import { chromium } from 'playwright-core';
import fs from 'fs';
const OUT = process.argv[2];
const b = await chromium.launch({ channel: 'chrome' });
const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
await p.goto(new URL('../../maris-2-v4.html', import.meta.url).href); await p.waitForSelector('.lg-go');
const d = await p.evaluate(() => {
  const fleet = {}; MAP_YARDS.forEach(c => { fleet[c] = yardTracks(c); });
  return { YARDS, MAP_YARDS, USER_GROUPS, fleet, MAPS, SHOPS, logo: document.querySelector('.bart-logo').src };
});
fs.writeFileSync(OUT + '/fleet.json', JSON.stringify({ yards: d.YARDS, mapYards: d.MAP_YARDS, fleet: d.fleet }));
fs.writeFileSync(OUT + '/userGroups.json', JSON.stringify(d.USER_GROUPS, null, 1));
fs.writeFileSync(OUT + '/maps.json', JSON.stringify({ MAPS: d.MAPS, SHOPS: d.SHOPS }));
fs.writeFileSync(OUT + '/bart-logo.png', Buffer.from(d.logo.split(',')[1], 'base64'));
console.log(d.logo.slice(0, 30), Object.keys(d.MAPS), Object.keys(d.SHOPS));
await b.close();
