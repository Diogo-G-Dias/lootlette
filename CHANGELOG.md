# Changelog

All notable changes to Lootlette are documented here.

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
