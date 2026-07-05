# Changelog

All notable changes to Lootlette are documented here.

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
