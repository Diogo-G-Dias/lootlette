# Changelog

All notable changes to Lootlette are documented here.

## [1.0.12] - 2026-08-18

### Fixed
- The **"cha-ching" no longer fires on ordinary coin drops** at the God Wars Dungeon. Every GWD minion's wiki
  page lists its large Coins drop under the same "Unique" header as the god-item pieces, so Coins was bundled
  into their tagged unique set; it's now blocklisted the way Phantom Muspah's Ancient essence already was.
- A kill that drops **nothing** now lands the reel on "Nothing" instead of free-spinning until the timeout.
  RuneLite posts no loot event at all for an empty drop, so it's inferred from the absence of one: a two-tick
  deadline armed when the corpse despawns (twelve ticks for the Nightmare, Duke Sucellus and the Swamp
  wallbeast, whose loot RuneLite deliberately delays). Affects any monster with a common "Nothing" row, such
  as TzHaar-Ket. (#4)

### Changed
- The reel count no longer **spoils a unique before the reel stops**. A wiki drop table is really several
  sub-tables, each rolled its own number of times, so a kill now spins the sum of those roll counts rather
  than just the biggest one. The Corrupted Gauntlet spins four reels on every kill (three supplies plus one
  independent unique roll that lands on "Nothing" when nothing drops); superior slayer monsters get the same
  treatment for their imbued heart / eternal gem roll. 66 monsters gain one or two reels; the rest are
  unchanged. (#5)

### Data
- **The Gauntlet and Corrupted Gauntlet now have real drop tables.** Their loot is credited to the Hunllef,
  but the wiki keeps both difficulties' tables on the shared Reward Chest (The Gauntlet) page, which nothing
  looked up — so the reel fell back to "drops seen this session" and armour seeds, enhanced seeds, Youngllef
  and dragon halberds never appeared at all. The scraper now reads each drop line's section anchor and files
  the Regular and Corrupted sections under Crystalline Hunllef and Corrupted Hunllef, with their uniques
  tagged and Youngllef on the dedicated pet reel. (#5)
- Refreshed the bundled drop snapshot: 2,090 -> 2,130 monsters, 3,487 -> 3,513 item ids.

## [1.0.11] - 2026-07-10

### Fixed
- The Nightmare / Phosani's Nightmare no longer rolls a **second, duplicate slot** for the same kill. Its
  ground loot pile spawns ~10 ticks after death, so the kill's loot arrived via two events on different
  ticks and slipped past the same-tick dedupe; loot claims are now tracked per event source with a
  cross-source window, while rapid repeat harvests (single-source) still roll every yield.

## [1.0.10] - 2026-07-08

### Data
- Removed **Ancient essence** and **Frozen cache** from Phantom Muspah's tagged unique set. They sit under the
  wiki's "Unique" header but are common drops, so the "cha-ching" was firing on nearly every Muspah kill; the
  real uniques (Ancient icon, Venator shard) still trigger it.

## [1.0.7] - 2026-07-07

### Added
- **Max reels** setting (Advanced, default 5): the most reels shown for a single kill or raid chest. Set it
  to 1 for a single slot that lands on the rarest/most unique drop.

### Changed
- A kill now shows **one slot with up to "Max reels" reels** (one per notable drop) instead of collapsing to
  a single reel — bosses like the Nightmare that drop several items at once show them together again.
- **Uniques are prioritised** when the reel cap trims: a monster's tagged uniques (and, as a fallback, any
  very-rare drop tinted that colour) are always kept and trigger the "cha-ching" — even relatively common
  uniques the rarity colour alone would miss, such as the Sarachnis cudgel.

### Data
- Bundled a `uniques` set scraped from the OSRS Wiki drop-table sections (51 monsters, 236 items), with a
  manual supplement for bosses whose page has no "Uniques" heading (Vorkath, Alchemical Hydra, Sarachnis,
  Scurrius, Obor, Bryophyta, Brutus).

## [1.0.6] - 2026-07-05

### Changed
- **One slot per kill.** Each NPC kill now shows a single reel instead of one per drop-table roll.
- The single slot lands on the **rarest** item you received, so a unique always displays instead of a
  normal drop when you got more than one item. Ranked by drop chance (rarest first), tie-broken by value.

### Data
- Refreshed the bundled drop-table snapshot from the OSRS Wiki (2061 → 2089 monsters). New content
  includes the Venator/Stymphike drops, superior venator, maggot king, dire bat, impaler deer,
  ancient feral vyre and others.

## [1.0.5] - 2026-06-27

- Draw the chest reel above the reward-chest UI so it isn't hidden behind the interface.
- Roll again on repeat instanced-boss kills (reused NPC index re-arms the death spin).

## [1.0.4]

- Theatre of Blood / Tombs of Amascut chest detection.
- Unique-drop "cha-ching" sound and reel fade-out.
- Config cleanup.

## [1.0.3]

- Fix the Nightmare shield break registering as a death.
- Tombs of Amascut chest reel.
- Right-click NPC ignore option.
- Doom of Mokhaiotl support.

## [1.0.2]

- Hide rare/gem/mega drop table option.
- Resolve untradeable items (pets, clue scrolls, boss uniques) so they render in the reel.
- Fix raid and boss roll handling.
- Suppress the reel for guaranteed-only drop tables.

## [1.0.0]

- Initial release: slot-machine reel of a monster's drop table on death, with Sailing harvests, raids
  reward chests, vertical/horizontal display modes, and a bundled wiki drop-table snapshot.
