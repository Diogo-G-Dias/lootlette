// Build-time scraper: pulls the entire OSRS Wiki `dropsline` bucket and writes a trimmed,
// gzipped drop-table snapshot the plugin loads at runtime (no live request needed).
//
// Run: node scripts/scrape-drops.mjs
// Output: src/main/resources/com/github/diogogdias/loulette/drops.json.gz
//
// Schema (keyed by lower-cased monster/page name):
//   { "lastUpdated": "YYYY-MM-DD", "monsters": { "<page>": [ [name, rarity, rolls], ... ] } }
// rolls is omitted (defaults to 1) when 1. "Nothing"/empty rows are dropped.

import { writeFileSync, mkdirSync } from "node:fs";
import { gzipSync } from "node:zlib";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const API = "https://oldschool.runescape.wiki/api.php";
const PAGE = 5000;
const UA = "loulette RuneLite plugin (build scraper; github.com/diogogdias/loulette)";

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, "../src/main/resources/com/github/diogogdias/loulette/drops.json.gz");

async function fetchPage(offset) {
  const query =
    `bucket('dropsline').select('page_name','item_name','drop_json')` +
    `.limit(${PAGE}).offset(${offset}).run()`;
  const url = `${API}?action=bucket&format=json&query=${encodeURIComponent(query)}`;
  const res = await fetch(url, { headers: { "User-Agent": UA } });
  if (!res.ok) throw new Error(`HTTP ${res.status} at offset ${offset}`);
  const json = await res.json();
  return json.bucket ?? [];
}

const monsters = {};
let total = 0, kept = 0;

for (let offset = 0; ; offset += PAGE) {
  const rows = await fetchPage(offset);
  if (rows.length === 0) break;
  total += rows.length;

  for (const row of rows) {
    const page = (row.page_name || "").trim();
    const name = (row.item_name || "").trim();
    if (!page || !name || name.toLowerCase() === "nothing") continue;

    let rarity = "", rolls = 1;
    if (row.drop_json) {
      try {
        const dj = JSON.parse(row.drop_json);
        rarity = (dj.Rarity ?? "").toString();
        const r = parseInt(dj.Rolls, 10);
        if (Number.isFinite(r) && r > 0) rolls = r;
      } catch { /* keep defaults */ }
    }

    const key = page.toLowerCase();
    (monsters[key] ??= []).push(rolls === 1 ? [name, rarity] : [name, rarity, rolls]);
    kept++;
  }

  process.stderr.write(`offset ${offset}: +${rows.length} rows (kept ${kept}/${total})\n`);
  if (rows.length < PAGE) break;
}

const now = new Date();
const lastUpdated = now.toISOString().slice(0, 10);
const payload = { lastUpdated, monsters };
const raw = Buffer.from(JSON.stringify(payload));
const gz = gzipSync(raw, { level: 9 });

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, gz);

process.stderr.write(
  `\nmonsters: ${Object.keys(monsters).length}\n` +
  `drop lines: ${kept}\n` +
  `raw JSON: ${(raw.length / 1e6).toFixed(2)} MB | gzipped: ${(gz.length / 1e6).toFixed(2)} MB\n` +
  `written: ${OUT}\n`
);
