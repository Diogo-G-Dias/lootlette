// One-shot: enrich the already-bundled snapshot WITHOUT a full re-scrape. Loads drops.json.gz,
// flags rare/gem/mega drop-table rows and adds the item name -> id map, then rewrites the gz.
// Run: node scripts/enrich.mjs
//
// The same two steps run as the tail of scrape-drops.mjs, so a future full scrape carries them too;
// this script just avoids re-downloading 2,000+ drop tables to add them today.

import { readFileSync, writeFileSync } from "node:fs";
import { gzipSync, gunzipSync } from "node:zlib";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { fetchCanonical, flagRareDropTable } from "./rdt-detect.mjs";
import { fetchItemIdMap, itemIdsFor } from "./item-ids.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, "../src/main/resources/com/github/diogogdias/loulette/drops.json.gz");

const payload = JSON.parse(gunzipSync(readFileSync(OUT)).toString("utf8"));
process.stderr.write(`loaded ${Object.keys(payload.monsters).length} monsters (updated ${payload.lastUpdated})\n`);

const base = await fetchCanonical();
const flagged = flagRareDropTable(payload.monsters, base);
process.stderr.write(`flagged ${flagged} rare/gem/mega drop-table rows\n`);

const fullMap = await fetchItemIdMap();
payload.items = itemIdsFor(payload.monsters, fullMap);
process.stderr.write(`item ids: ${Object.keys(payload.items).length} mapped (of ${Object.keys(fullMap).length} known)\n`);

const raw = Buffer.from(JSON.stringify(payload));
const gz = gzipSync(raw, { level: 9 });
writeFileSync(OUT, gz);

process.stderr.write(
	`raw JSON: ${(raw.length / 1e6).toFixed(2)} MB | gzipped: ${(gz.length / 1e6).toFixed(2)} MB\n` +
	`written: ${OUT}\n`
);
