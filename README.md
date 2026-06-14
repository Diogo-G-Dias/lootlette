# Lootlette

A RuneLite plugin that turns every drop into a slot machine. The moment a monster dies — or a Sailing
creature is harvested — a reel spins over the spot and lands on what you actually got: one reel per loot
roll, including the rolls that land on **Nothing**, each cell tinted by the item's drop rarity.

## How it works

- **Kills:** the instant a monster's HP hits zero, a reel starts free-spinning over its tile; when the
  loot resolves it settles on what dropped.
- **Sailing harvests:** harvestable creatures aren't killed, they're netted, so they deliver loot through
  the game's server loot event (`ServerNpcLoot`) instead of a death. Lootlette handles those too — the
  reel spins while you harvest (even at a distance) and lands on the yield.
- **Raids reward chests:** Chambers of Xeric, Theatre of Blood and Tombs of Amascut roll off the chest
  loot (shown on the horizontal reel, since there's no tile to anchor to).
- It shows **one reel per drop-table roll**. The roll count comes from the drop table (the main table's
  roll count; usually 1, more for multi-roll bosses), bumped up if you received more items than that.
  Your drops fill reels; leftover rolls land on a grey **Nothing** symbol.
- **Always-drops are ignored** — guaranteed drops (bones, ashes, etc., rarity `Always`) don't take a
  reel and aren't counted as a roll.
- Each cell is tinted by the item's **drop rarity** (see below); the reel pool of "possible items" is the
  monster's full drop table.

## Display

- **Vertical reels** (default) render in a cluster over the tile where the loot came from.
- **Horizontal reel** (optional) shows a single CS:GO case-opening style strip fixed at the top centre of
  the screen instead. Raids chests always use this mode.
- **Slot size** scales both the reel cells and the item icons.

## Drop-table data

Drop tables ship **bundled** with the plugin (`drops.json.gz`, a trimmed snapshot of the entire OSRS Wiki
`dropsline` bucket — ~2,000 monsters, ~180 KB gzipped). That means full reels — every possible drop, roll
counts, always-drop filtering, rarity colours and `Nothing` results — work out of the box with **no
network request**.

Regenerate the snapshot from the wiki any time with `node scripts/scrape-drops.mjs`, then commit the
updated `drops.json.gz`. Re-run it when the game adds new content.

For monsters **not** in the snapshot, turn on **Live wiki fallback** to fetch their table from the OSRS
Wiki on demand (off by default — it sends your IP to a third-party server). With both the snapshot miss
and the fallback off, the plugin still spins, using only drops you've already seen this session (no roll
counts, always-drop filtering, rarity colours or Nothing results).

## Config

| Setting | Default | Notes |
|---|---|---|
| Settle time (ms) | 800 | How quickly the reels settle on the result once the loot resolves. |
| Spin speed | 14 | How fast the reels spin while free-spinning (items per second). |
| Result hold time (ms) | 2500 | How long the landed result stays on screen before it fades. |
| Slot size (%) | 100 | Scales the reel slots and the item icons (50–250%). |
| Horizontal reel (top centre) | off | Single CS:GO-style strip at the top centre instead of reels over the tile. |
| Max reels per kill | 8 | Cap on how many reels (rolls) are shown at once (1–12). |
| Min item value (gp) | 0 | Drops below this value roll to 'Nothing'. 0 = count everything. |
| Live wiki fallback | off | Fetch tables live from the wiki for monsters missing from the bundled snapshot. Off by default because it sends your IP to a third-party server. |

### Rarity colours

Each cell is coloured by the item's drop chance. All colours are configurable; defaults:

| Grade | Drop chance | Default |
|---|---|---|
| Always | 100% | pale cyan |
| Common | 1/25 or better | green |
| Uncommon | 1/25 – 1/100 | yellow |
| Rare | 1/100 – 1/1000 | orange |
| Very rare | rarer than 1/1000 | red |
| Varies / random | varies | pink |

## License

BSD-2-Clause. Drop-table data © the OSRS Wiki contributors (CC BY-NC-SA).
