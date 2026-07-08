// Build-time: scrape each monster page's wikitext for its "Uniques" drop-table section, producing a
// page -> [unique item names] map. The `dropsline` bucket flattens the section header away, so the
// uniques (the drops players actually farm a boss for) can't be told apart from junk by rarity alone —
// a 1/128 Sarachnis cudgel is a unique, a 1/500 secondary is not. We read the section headers straight
// from wikitext. For bosses whose page doesn't use a ===Uniques=== section (their uniques sit under
// generic headers like "Weapons and armour" / "Pre-roll" / "Tertiary"), a small hand-maintained
// supplement fills the gap.

const API = "https://oldschool.runescape.wiki/api.php";
const UA = "loulette RuneLite plugin (build scraper; github.com/diogogdias/loulette)";
const BATCH = 50;

// Common resources filed under Phantom Muspah's "Unique" header (Ancient essence, Frozen cache) that aren't
// trophies — leaving them tagged makes the cha-ching fire on ordinary Muspah drops. Lower-cased, exact match.
const BLOCKLIST = new Set(["ancient essence", "frozen cache"]);

// Bosses with no ===Uniques=== section on their wiki page. Keyed by lower-cased page name.
const MANUAL = {
  "vorkath": ["Skeletal visage", "Draconic visage", "Dragonbone necklace", "Jar of decay", "Vorki", "Vorkath's head"],
  "alchemical hydra": ["Hydra's claw", "Hydra's eye", "Hydra's fang", "Hydra's heart", "Hydra tail",
    "Hydra leather", "Alchemical hydra heads", "Jar of chemicals", "Ikkle hydra"],
  "sarachnis": ["Sarachnis cudgel", "Jar of eyes", "Sraracha"],
  "scurrius": ["Scurrius' spine", "Scurry"],
  "obor": ["Hill giant club"],
  "bryophyta": ["Bryophyta's essence"],
  "brutus": ["Mooleta", "Bottomless milk bucket (empty)", "Cow slippers"],
};

const HEADER = /^\s*===+\s*(.*?)\s*===+\s*$/;
const DROPLINE = /\{\{DropsLine\s*\|(.*)$/i;
const isUniqueHeader = (h) => /unique/i.test(h);
const nameOf = (params) => {
  const m = params.match(/(?:^|\|)\s*name\s*=\s*([^|}]+)/i);
  return m ? m[1].trim() : null;
};

/** Fetches up to 50 pages' wikitext in one request; returns a lower-cased-title -> wikitext map. */
async function wikitextBatch(titles) {
  const url = `${API}?action=query&format=json&prop=revisions&rvprop=content&rvslots=main`
    + `&titles=${encodeURIComponent(titles.join("|"))}`;
  const res = await fetch(url, { headers: { "User-Agent": UA } });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const j = await res.json();
  const out = {};
  for (const p of Object.values(j.query?.pages ?? {})) {
    out[(p.title || "").toLowerCase()] = p.revisions?.[0]?.slots?.main?.["*"] ?? "";
  }
  return out;
}

/** Collects item names under any ===Uniques=== (or "Unique …") drop-table section in one page's wikitext. */
function extractUniques(wikitext) {
  let section = null;
  const names = [];
  for (const line of wikitext.split("\n")) {
    const h = line.match(HEADER);
    if (h) { section = h[1]; continue; }
    if (section && isUniqueHeader(section)) {
      const d = line.match(DROPLINE);
      if (d) { const n = nameOf(d[1]); if (n) names.push(n); }
    }
  }
  return names;
}

/**
 * Scrapes uniques for every page title, merges the manual supplement, and drops any unique whose name
 * doesn't resolve to an item id (a spelling drift between the wiki section and the item bucket).
 *
 * @param titles   original-case page titles to scan
 * @param itemMap  lower-cased item name -> id (used only to filter unresolved names)
 * @param log      stderr logger
 * @returns { "<lower page>": [names] } for pages that have any resolvable uniques
 */
export async function scrapeUniques(titles, itemMap, log = () => {}) {
  const uniques = {};
  for (let i = 0; i < titles.length; i += BATCH) {
    const chunk = titles.slice(i, i + BATCH);
    let wt;
    try { wt = await wikitextBatch(chunk); }
    catch (e) { log(`uniques: wikitext batch @${i} failed: ${e.message}`); continue; }
    for (const title of chunk) {
      const text = wt[title.toLowerCase()];
      if (!text) continue;
      const names = extractUniques(text);
      if (names.length) uniques[title.toLowerCase()] = names;
    }
    log(`uniques: scanned ${Math.min(i + BATCH, titles.length)}/${titles.length}\r`);
  }

  // Union in the manual supplement for bosses without a Uniques section.
  for (const [page, names] of Object.entries(MANUAL)) {
    const set = new Set(uniques[page] ?? []);
    names.forEach((n) => set.add(n));
    uniques[page] = [...set];
  }

  // Keep only names that resolve to an item id and aren't blocklisted non-trophy filler; warn on the rest
  // so drift is visible at build time.
  let kept = 0, dropped = 0;
  for (const [page, names] of Object.entries(uniques)) {
    const resolved = names.filter((n) => n.toLowerCase() in itemMap && !BLOCKLIST.has(n.toLowerCase()));
    for (const n of names) {
      if (!(n.toLowerCase() in itemMap)) { log(`uniques: '${n}' (${page}) has no item id — skipped\n`); dropped++; }
    }
    if (resolved.length) { uniques[page] = resolved; kept += resolved.length; }
    else delete uniques[page];
  }
  log(`uniques: ${Object.keys(uniques).length} monsters, ${kept} unique items (${dropped} unresolved)\n`);
  return uniques;
}
