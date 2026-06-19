// Rare/Gem/Mega-rare drop table detection.
//
// The OSRS Wiki materialises the shared Rare drop table (and its nested Gem and Mega-rare
// branches) into every monster's drop list with per-monster scaled rarities, and gives them
// NO flag distinguishing them from the monster's own drops. Worse, their item names collide
// with real drops (e.g. Araxxor drops Rune kiteshield at 8/115 itself AND at 1/14,720 via the
// RDT). So they can't be removed by name alone.
//
// A monster accesses each sub-table at one fixed rate, so every genuine RDT row shares the same
// "access multiplier" m = row_chance / base_chance (base = the item's chance within its sub-table
// page). We cluster the canonical-named rows of a monster by m; a cluster of >= 2 distinct items
// sharing an m that's small (a rare access) is the sub-table, and its rows get flagged. A real
// colliding drop (Rune kiteshield 8/115) has a different, larger m and stays a singleton, so it
// survives. Flagging is per-ROW: an item is only dropped from the reel pool when every one of its
// rows is flagged.

const API = "https://oldschool.runescape.wiki/api.php";
const UA = "loulette RuneLite plugin (build scraper; github.com/diogogdias/loulette)";
const SUBTABLES = ["Rare drop table", "Gem drop table", "Mega-rare drop table"];

const M_MAX = 0.5;       // a sub-table is always accessed rarely; ignore clusters more common than this
const TOLERANCE = 1.06;  // rows within 6% of each other share an access rate
const MIN_CLUSTER = 3;   // a real shared table contributes many items; 2 matches is a coincidence (e.g. a
                         // monster's own coins + a rune both happening to land on one access rate), not a table

export function parseFrac(s)
{
	s = (s || "").replace(/,|~/g, "").trim();
	const m = s.match(/^([0-9.]+)\/([0-9.]+)$/);
	return m ? Number(m[1]) / Number(m[2]) : null;
}

async function wikitext(page)
{
	const url = `${API}?action=parse&format=json&prop=wikitext&page=${encodeURIComponent(page)}`;
	const res = await fetch(url, { headers: { "User-Agent": UA } });
	if (!res.ok) throw new Error(`HTTP ${res.status} fetching '${page}'`);
	const json = await res.json();
	return json.parse ? json.parse.wikitext["*"] : "";
}

/** Builds name -> [base chance, ...] across the RDT/Gem/Mega sub-table pages. */
export async function fetchCanonical()
{
	const base = {};
	for (const page of SUBTABLES)
	{
		const t = await wikitext(page);
		for (const block of t.match(/\{\{DropsLine[^}]*\}\}/gi) || [])
		{
			const name = (block.match(/\|\s*name\s*=\s*([^|}]+)/i) || [])[1];
			const frac = parseFrac((block.match(/\|\s*rarity\s*=\s*([^|}]+)/i) || [])[1]);
			if (name && frac) (base[name.trim()] ??= []).push(frac);
		}
	}
	return base;
}

/** True if a single monster's line (a row) belongs to a rare/gem/mega sub-table. */
function flagMonster(lines, base)
{
	// Each canonical-named row contributes a candidate access multiplier per base chance it could match.
	const cands = [];
	for (const line of lines)
	{
		const frac = parseFrac(line[1]);
		if (!frac || !base[line[0]]) continue;
		for (const bf of base[line[0]]) cands.push({ line, m: frac / bf });
	}
	cands.sort((a, b) => a.m - b.m);

	const flagged = new Set(); // the actual line objects that are RDT rows
	for (let i = 0; i < cands.length; i++)
	{
		const cluster = [cands[i]];
		for (let k = i + 1; k < cands.length && cands[k].m <= cands[i].m * TOLERANCE; k++)
		{
			cluster.push(cands[k]);
		}
		const names = new Set(cluster.map(c => c.line[0]));
		if (names.size >= MIN_CLUSTER && cluster[0].m <= M_MAX)
		{
			for (const c of cluster) flagged.add(c.line);
		}
	}
	return flagged;
}

/**
 * Mutates the {@code monsters} map in place, appending a trailing {@code 1} to every line that is a
 * rare/gem/mega-table row: {@code [name, rarity]} / {@code [name, rarity, rolls]} -> {@code [name, rarity, rolls, 1]}.
 * Returns the number of rows flagged.
 */
export function flagRareDropTable(monsters, base)
{
	let flaggedCount = 0;
	for (const lines of Object.values(monsters))
	{
		const flagged = flagMonster(lines, base);
		for (const line of lines)
		{
			if (!flagged.has(line)) continue;
			if (line.length < 3) line.push(1); // ensure rolls slot exists
			line.push(1);                       // rdt marker
			flaggedCount++;
		}
	}
	return flaggedCount;
}
