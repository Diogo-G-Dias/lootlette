// Build-time item name -> item id map.
//
// At runtime the plugin needs an icon for every droppable item, but RuneLite's ItemManager.search()
// only knows GE-tradeable items, so untradeables (pets, clue scrolls, brimstone keys, boss uniques
// like Nid/Araxyte head/Noxious blade) never resolve and silently vanish from the reel. The wiki's
// `infobox_item` bucket has the in-game id for essentially every item, tradeable or not, so we bundle
// a name -> id map and resolve from it first.

const API = "https://oldschool.runescape.wiki/api.php";
const UA = "loulette RuneLite plugin (build scraper; github.com/diogogdias/loulette)";
const PAGE = 5000;

async function bucket(query)
{
	const url = `${API}?${new URLSearchParams({ action: "bucket", format: "json", query })}`;
	const res = await fetch(url, { headers: { "User-Agent": UA } });
	if (!res.ok) throw new Error(`HTTP ${res.status}`);
	return (await res.json()).bucket ?? [];
}

/** Pages the whole `infobox_item` bucket into a lower-cased name -> numeric id map (first id wins). */
export async function fetchItemIdMap()
{
	const map = {};
	for (let offset = 0; ; offset += PAGE)
	{
		const rows = await bucket(`bucket('infobox_item').select('item_name','item_id').limit(${PAGE}).offset(${offset}).run()`);
		if (rows.length === 0) break;
		for (const row of rows)
		{
			const name = (row.item_name || "").trim();
			const raw = Array.isArray(row.item_id) ? row.item_id[0] : row.item_id;
			const id = Number.parseInt(raw, 10);
			const key = name.toLowerCase();
			if (name && Number.isFinite(id) && !(key in map)) map[key] = id;
		}
		if (rows.length < PAGE) break;
	}
	return map;
}

/** Picks the {@code name -> id} entries for the item names that actually appear in {@code monsters}. */
export function itemIdsFor(monsters, fullMap)
{
	const out = {};
	for (const lines of Object.values(monsters))
	{
		for (const line of lines)
		{
			const key = String(line[0]).toLowerCase();
			if (!(key in out) && key in fullMap) out[key] = fullMap[key];
		}
	}
	return out;
}
