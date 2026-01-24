const pad2 = (n) => String(n).padStart(2, '0');
const pad3 = (n) => String(n).padStart(3, '0');

const toIsoKst = (y, mo, d, h = 0, mi = 0, s = 0, ms = 0) =>
`${y}-${pad2(mo)}-${pad2(d)}T${pad2(h)}:${pad2(mi)}:${pad2(s)}.${pad3(ms)}+09:00`;

const parseServerDate = (v) => {
if (!v) return null;

if (v instanceof Date) return Number.isNaN(v.getTime()) ? null : v;

if (typeof v === 'number') {
const d = new Date(v);
return Number.isNaN(d.getTime()) ? null : d;
}
if (typeof v === 'string' && /^\d+$/.test(v.trim())) {
const d = new Date(Number(v.trim()));
return Number.isNaN(d.getTime()) ? null : d;
}

// [2026,1,20,18,41,9,0] or nanos at index 6
if (Array.isArray(v)) {
const [y, mo, d, h = 0, mi = 0, s = 0, nanoOrMs = 0] = v;
const ms = nanoOrMs > 999 ? Math.floor(nanoOrMs / 1_000_000) : nanoOrMs;
const dt = new Date(toIsoKst(y, mo, d, h, mi, s, ms));
return Number.isNaN(dt.getTime()) ? null : dt;
}

const s = String(v).trim();

// already ISO w/ tz
if (/Z$/.test(s) || /[+-]\d{2}:\d{2}$/.test(s)) {
const d = new Date(s);
return Number.isNaN(d.getTime()) ? null : d;
}

// "2026-01-20 18:41:09.000" -> ISO +09:00
if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(\.\d+)?$/.test(s)) {
const iso = s.replace(' ', 'T') + '+09:00';
const d = new Date(iso);
return Number.isNaN(d.getTime()) ? null : d;
}

// "2026-01-20T18:41:09" -> assume KST
if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/.test(s)) {
const d = new Date(s + '+09:00');
return Number.isNaN(d.getTime()) ? null : d;
}

const d = new Date(s);
return Number.isNaN(d.getTime()) ? null : d;
};

export const formatKst = (v) => {
const d = parseServerDate(v);
return d ? d.toLocaleString('ko-KR') : '-';
};
