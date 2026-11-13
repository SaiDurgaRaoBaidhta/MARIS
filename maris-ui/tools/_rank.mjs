import fs from 'fs'; import { PNG } from 'pngjs';
const D = process.argv[2]; const out = [];
for (const f of fs.readdirSync(D).filter(f => f.endsWith('.diff.png'))) { const p = PNG.sync.read(fs.readFileSync(D + '/' + f)); let n = 0;
  for (let i = 0; i < p.data.length; i += 4) if (p.data[i] > 200 && p.data[i + 1] < 80) n++; out.push([f.replace('.diff.png', ''), +(n / (p.width * p.height)).toFixed(4)]); }
out.sort((a, b) => b[1] - a[1]); console.log(out.filter(x => !x[0].startsWith('egl_')).map(x => x.join(' ')).join('\n'));
