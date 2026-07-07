// Build-time pet detection.
//
// In OSRS a boss/monster pet is rolled on a SEPARATE roll from the main drop table, but the wiki's
// `dropsline` bucket lists the pet as an ordinary drop row (no marker, Rolls defaults to 1), so the
// plugin can't tell it apart from a normal drop. We flag pets here so the reel can spin a dedicated
// pet roll. Source of truth is the wiki's "Pets" category; we keep only the pet names that actually
// appear as a drop row in the bundled snapshot.

const API = "https://oldschool.runescape.wiki/api.php";
const UA = "loulette RuneLite plugin (build scraper; github.com/diogogdias/loulette)";

/** Fetches the wiki "Pets" category as a set of lower-cased item names (disambiguation suffixes stripped). */
export async function fetchPets()
{
	const url = `${API}?${new URLSearchParams({
		action: "query", format: "json", list: "categorymembers",
		cmtitle: "Category:Pets", cmlimit: "500", cmtype: "page",
	})}`;
	const res = await fetch(url, { headers: { "User-Agent": UA } });
	if (!res.ok) throw new Error(`HTTP ${res.status}`);
	const members = (await res.json()).query?.categorymembers ?? [];
	// A pet's category page can carry a " (pet)"/" (item)" disambiguation suffix its item name doesn't.
	const norm = (s) => s.replace(/\s*\((pet|item)\)$/i, "").toLowerCase().trim();
	return new Set(members.map((m) => norm(m.title)));
}

/** Sorted list of pet item names (lower-cased) that appear as a drop row in {@code monsters}. */
export function petNamesFor(monsters, petSet)
{
	const out = new Set();
	for (const lines of Object.values(monsters))
	{
		for (const line of lines)
		{
			const key = String(line[0]).toLowerCase();
			if (petSet.has(key)) out.add(key);
		}
	}
	return [...out].sort();
}
