// Produces golden test vectors by running the *browser's own* gt2 encoding functions
// (copied at run time, read-only, from MARISWeb/WebContent/sync-gt2-vast-support.layer.js).
// Usage: node extract-gt2-vectors.mjs <path-to-layer.js> <output.json>
import fs from 'node:fs';

const [layerPath, outPath] = process.argv.slice(2);
const src = fs.readFileSync(layerPath, 'latin1');

function moduleBody(name) {
  const key = `"com/sync/gt2/vagen/model/dojo/${name}":function(){`;
  const i = src.indexOf(key);
  if (i < 0) throw new Error('module not found ' + name);
  return src.slice(i, i + 6000);
}
// Copy one method's source text "name:function(...){...}" using brace matching.
function method(text, name) {
  const i = text.indexOf(name + ':function(');
  if (i < 0) throw new Error('method not found ' + name);
  let j = text.indexOf('{', i), depth = 0;
  for (let k = j; k < text.length; k++) {
    if (text[k] === '{') depth++;
    if (text[k] === '}' && --depth === 0) return '(' + text.slice(i + name.length + 1, k + 1) + ')';
  }
  throw new Error('unbalanced ' + name);
}
const pad = (t, size, ch = '0', end = false) => { t = String(t); while (t.length < size) t = end ? t + ch : ch + t; return t; };
globalThis.dojo = {
  string: { pad, rep: (c, n) => c.repeat(n) },
  number: { round: (v, p) => { const f = Math.pow(10, p || 0); return Math.round(v * f) / f; }, parse: Number },
  isString: (v) => typeof v === 'string',
};
globalThis.framework = { unboxAny: (v) => v, getTypeSignatureInfo: () => null };

const numeric = moduleBody('ITFNumericDataItem');
const packed = moduleBody('ITFPackedDecimalDataItem');
const pack = moduleBody('ITFPackDataItem');
const pacf = moduleBody('ITFPacfDataItem');
const bin = moduleBody('ITFBinDataItem');
const nonNum = moduleBody('ITFNonNumericDataItem');
const ev = (t, m) => eval(method(t, m));

function base(length, decimals) {
  return {
    length: () => length, decimalPositions: () => decimals,
    hptPositivePowersOf10: [1, 10, 100, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19],
    hptTruncate_PrecisionTo_: ev(numeric, 'hptTruncate_PrecisionTo_'),
    hptAsITFNumber_: ev(numeric, 'hptAsITFNumber_'),
    hptAsInteger_WithImplicitDecimals_: ev(numeric, 'hptAsInteger_WithImplicitDecimals_'),
    adjustValue_allowInsignificantTruncation_: ev(numeric, 'adjustValue_allowInsignificantTruncation_'),
    internalValueFrom_: ev(numeric, 'internalValueFrom_'),
  };
}
const make = {
  NUM: (l, d) => ({ ...base(l, d), bytes: () => l,
    externalValueFrom_allowInsignificantTruncation_: ev(numeric, 'externalValueFrom_allowInsignificantTruncation_'),
    hptAsHptNumString_OfSize_: ev(numeric, 'hptAsHptNumString_OfSize_'),
    asIntegerWithImplicitDecimals: ev(numeric, 'asIntegerWithImplicitDecimals') }),
  DECIMAL: (l, d) => ({ ...base(l, d), bytes: () => Math.ceil((l + 1) / 2), positiveSignCode: () => 12,
    externalValueFrom_allowInsignificantTruncation_: ev(pack, 'externalValueFrom_allowInsignificantTruncation_'),
    hptAsHptPackString_OfSize_: ev(pack, 'hptAsHptPackString_OfSize_'),
    asIntegerWithImplicitDecimals: ev(packed, 'asIntegerWithImplicitDecimals') }),
  PACF: (l, d) => ({ ...base(l, d), bytes: () => Math.ceil((l + 1) / 2), positiveSignCode: () => 15,
    externalValueFrom_allowInsignificantTruncation_: ev(pacf, 'externalValueFrom_allowInsignificantTruncation_'),
    hptAsHptPacfString_OfSize_: ev(pacf, 'hptAsHptPacfString_OfSize_'),
    asIntegerWithImplicitDecimals: ev(packed, 'asIntegerWithImplicitDecimals') }),
  BIN: (l, d) => ({ ...base(l, d), highOrder: false, maxValMap: { 2: 65536, 4: 4294967296, 8: 1.8446744073709552e19 },
    bytes: () => ({ 4: 2, 9: 4, 18: 8 })[l],
    externalValueFrom_allowInsignificantTruncation_: ev(bin, 'externalValueFrom_allowInsignificantTruncation_'),
    hptAsHptBinString_OfSize_: ev(bin, 'hptAsHptBinString_OfSize_'),
    asIntegerWithImplicitDecimals: ev(bin, 'asIntegerWithImplicitDecimals') }),
  CHAR: (l) => ({ length: () => l, adjustValue_: ev(nonNum, 'adjustValue_'), guiValueFromValue_: ev(nonNum, 'guiValueFromValue_') }),
};
const toHex = (s) => [...s].map((c) => c.charCodeAt(0).toString(16).padStart(2, '0')).join('');

const cases = [
  ['NUM', 3, 0, [0, 5, 123, -7, -123, 999]], ['NUM', 4, 2, [12.34, -1.5, 0.07, 0]], ['NUM', 7, 0, [2024123, 1]],
  ['NUM', 1, 0, [0, 9, -3]], ['NUM', 9, 0, [123456789, -987654321]],
  ['DECIMAL', 7, 2, [12345.67, -1.5, 0, 0.01]], ['DECIMAL', 5, 0, [99999, -1, 0]], ['DECIMAL', 8, 0, [12345678, -42]],
  ['DECIMAL', 3, 0, [123, -5]], ['DECIMAL', 9, 3, [123456.789, -0.001]],
  ['PACF', 3, 0, [12, 0]], ['BIN', 4, 0, [0, 1, -1, 32767, -32768, 300]], ['BIN', 9, 0, [70000, -2, 0, 2147483647]],
  ['BIN', 4, 2, [1.5, -2.25]],
];
const numericVectors = [];
for (const [type, length, decimals, values] of cases) {
  const item = make[type](length, decimals);
  for (const v of values) {
    const enc = item.externalValueFrom_allowInsignificantTruncation_(v, false);
    const back = item.internalValueFrom_ ? item.asIntegerWithImplicitDecimals(enc) / item.hptPositivePowersOf10[decimals] : null;
    numericVectors.push({ type, length, decimals, value: v, hex: toHex(enc), decoded: back });
  }
}
// Zoned decode of non-digit bytes the backend can return (e.g. a num field left as spaces).
const zoned = make.NUM(3, 0);
const zonedOddDecodes = ['   ', '00 ', '12p', '12y'].map((s) => ({ hex: toHex(s), decoded: zoned.asIntegerWithImplicitDecimals(s) }));

const charItem = make.CHAR(8);
const charVectors = ['BRIGHTCO', 'AB', 'TOOLONGVALUE', 'trail   ', ''].map((v) => ({
  length: 8, value: v, encoded: charItem.adjustValue_(v), gui: charItem.guiValueFromValue_(charItem.adjustValue_(v)) }));

const encodeFn = eval('(' + src.slice(src.indexOf('syncEncodeString_:function') + 'syncEncodeString_:'.length).split(',signalDataChanged:')[0] + ')');
const decodeFn = eval('(' + src.slice(src.indexOf('syncDecodeString_:function') + 'syncDecodeString_:'.length).split(',syncEncodeString_:')[0] + ')');
const samples = ['ABC 123', '100% "quoted" {brace} back\\slash', '\u0000\u0001\u001f\u007f\u0080éÿ', '~ tilde'];
const escapeVectors = samples.map((s) => ({ rawHex: toHex(s), escaped: encodeFn(s), roundTripHex: toHex(decodeFn(encodeFn(s))) }));

fs.writeFileSync(outPath, JSON.stringify({ source: 'sync-gt2-vast-support.layer.js', numericVectors, zonedOddDecodes, charVectors, escapeVectors }, null, 2));
console.log(`wrote ${numericVectors.length} numeric, ${charVectors.length} char, ${escapeVectors.length} escape vectors`);
