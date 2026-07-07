// One-shot: add the `pets` field to the already-bundled snapshot WITHOUT re-scraping or re-flagging
// anything else (monsters/items/lastUpdated are left byte-for-byte as they are). Run: node scripts/add-pets.mjs
//
// pet-detect + the pets field are also wired into scrape-drops.mjs and enrich.mjs, so a future full
// scrape or enrich carries them too; this script just backfills the current gz without disturbing it.

import { readFileSync, writeFileSync } from "node:fs";
import { gzipSync, gunzipSync } from "node:zlib";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { fetchPets, petNamesFor } from "./pet-detect.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, "../src/main/resources/com/github/diogogdias/loulette/drops.json.gz");

const payload = JSON.parse(gunzipSync(readFileSync(OUT)).toString("utf8"));
process.stderr.write(`loaded ${Object.keys(payload.monsters).length} monsters (updated ${payload.lastUpdated})\n`);

payload.pets = petNamesFor(payload.monsters, await fetchPets());
process.stderr.write(`pets: ${payload.pets.length} pet drop-rows flagged\n${payload.pets.join(", ")}\n`);

const raw = Buffer.from(JSON.stringify(payload));
const gz = gzipSync(raw, { level: 9 });
writeFileSync(OUT, gz);

process.stderr.write(
	`raw JSON: ${(raw.length / 1e6).toFixed(2)} MB | gzipped: ${(gz.length / 1e6).toFixed(2)} MB\n` +
	`written: ${OUT}\n`
);
