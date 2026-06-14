# Lootlette

A RuneLite plugin that turns every kill into a slot machine. The moment a monster dies, a reel
spins over the spot where it fell and lands on what you actually got — one reel per loot roll the
monster makes, including the rolls that land on **Nothing**.

## How it works

- Triggers on `NpcLootReceived` (the instant the monster dies and drops), anchored to its tile.
- It shows **one reel per drop-table roll**. The roll count comes from the OSRS Wiki (the main
  table's roll count; usually 1, more for multi-roll bosses), bumped up if you received more items
  than that. Your drops fill reels; leftover rolls land on a grey **Nothing** symbol.
- **Always-drops are ignored** — guaranteed drops (bones, ashes, etc., wiki rarity `Always`) don't
  take a reel and aren't counted as a roll.
- The reel pool of "possible items" is the monster's full drop table from a **bundled snapshot** of the
  [OSRS Wiki](https://oldschool.runescape.wiki) drop tables — no network request, works out of the box.

## Drop-table data

Drop tables ship **bundled** with the plugin (`drops.json.gz`, a trimmed snapshot of the whole OSRS Wiki
`dropsline` bucket, ~180 KB). That means full reels — every possible drop, roll counts, always-drop
filtering and `Nothing` results — work by default with no third-party request.

Regenerate the snapshot from the wiki any time with `node scripts/scrape-drops.mjs`. Re-run it when the
game adds new content and commit the updated file.

## Config

| Setting | Default | Notes |
|---|---|---|
| Roll duration (ms) | 2500 | How long the reels spin before landing. |
| Max reels per kill | 8 | Cap on reels (rolls) shown at once. |
| Min item value (gp) | 0 | Drops below this roll to 'Nothing'. 0 = count everything. |
| Live wiki fallback | off | Also fetch tables live from the wiki for monsters missing from the bundled snapshot (e.g. brand-new content). Off by default because it sends your IP to a third-party server. |

For monsters not in the snapshot **and** with the live fallback off, the plugin falls back to one reel
per item you received, spinning through drops you've already seen — no roll counts, always-drop
filtering, or Nothing results.

## License

BSD-2-Clause. Drop-table data © the OSRS Wiki contributors (CC BY-NC-SA).
