package com.github.diogogdias.loulette;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(LoulettePlugin.CONFIG_GROUP)
public interface LouletteConfig extends Config
{
	@ConfigSection(
		name = "Advanced",
		description = "Spin timing, reel pool size and drop-table options",
		position = 50,
		closedByDefault = true
	)
	String advanced = "advanced";

	@ConfigSection(
		name = "Rarity colours",
		description = "Colours used to tint each reel cell by the item's drop rarity",
		position = 100,
		closedByDefault = true
	)
	String rarityColours = "rarityColours";

	// ---- Top level (common) ----

	@ConfigItem(
		keyName = "horizontalReel",
		name = "Horizontal reel (top centre)",
		description = "Show a CS:GO case-opening style horizontal reel fixed at the top centre of the screen, "
			+ "instead of a vertical reel over the monster",
		position = 0
	)
	default boolean horizontalReel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "verticalAnchor",
		name = "Vertical reel anchor",
		description = "Where the vertical reel sits. South-west / centre tile follow the monster's footprint (centre "
			+ "only differs for 3x3+ NPCs); on top of player anchors the reel over your character instead.",
		position = 1
	)
	default VerticalAnchor verticalAnchor()
	{
		return VerticalAnchor.SOUTH_WEST;
	}

	@Range(min = 50, max = 250)
	@ConfigItem(
		keyName = "slotScale",
		name = "Slot size (%)",
		description = "Scale the size of the reel slots and the item icons inside them",
		position = 2
	)
	default int slotScale()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "uniqueDropSound",
		name = "Unique drop sound",
		description = "Play a 'cha-ching' sound when a reel lands on a unique drop — a raid purple, or a very rare "
			+ "(rarer than 1/1000) drop such as a pet or boss unique.",
		position = 3
	)
	default boolean uniqueDropSound()
	{
		return true;
	}

	@ConfigItem(
		keyName = "rightClickIgnore",
		name = "Right-click ignore",
		description = "Add a 'Lootlette-ignore' option to NPC right-click menus. Ignored NPCs never roll a reel; "
			+ "right-click again to 'Lootlette-unignore'. The ignore list persists across sessions.",
		position = 4
	)
	default boolean rightClickIgnore()
	{
		return false;
	}

	@ConfigItem(
		keyName = "ignoredNpcs",
		name = "Ignored NPCs",
		description = "Comma-separated NPC names that never roll a reel (managed via the right-click menu).",
		position = 5,
		hidden = true
	)
	default String ignoredNpcs()
	{
		// CoX Scavenger beasts deliver their loot via ServerNpcLoot (not the chest), so they slip past the
		// in-raid suppression and spin a reel on every kill. Ignore them out of the box; users can unignore.
		return "Scavenger beast";
	}

	// ---- Advanced ----

	@Range(min = 4, max = 40)
	@ConfigItem(
		keyName = "spinSpeed",
		name = "Spin speed",
		description = "How fast the reels spin while free-spinning (items per second)",
		section = advanced,
		position = 0
	)
	default int spinSpeed()
	{
		return 14;
	}

	@Range(min = 100, max = 3000)
	@ConfigItem(
		keyName = "settleMs",
		name = "Settle time (ms)",
		description = "How quickly the reels settle on the result once the loot spawns. The reels free-spin from "
			+ "the moment the monster's HP hits zero until then",
		section = advanced,
		position = 1
	)
	default int settleMs()
	{
		return 800;
	}

	@Range(min = 1000, max = 30000)
	@ConfigItem(
		keyName = "resultHoldMs",
		name = "Result hold time (ms)",
		description = "How long the landed result stays on screen after the reel settles, before it fades away",
		section = advanced,
		position = 2
	)
	default int resultHoldMs()
	{
		return 2500;
	}

	@Range(min = 0, max = 5000)
	@ConfigItem(
		keyName = "fadeMs",
		name = "Fade duration (ms)",
		description = "How long the landed result takes to fade out AFTER the result hold time elapses. 0 = no fade "
			+ "(disappears instantly).",
		section = advanced,
		position = 3
	)
	default int fadeMs()
	{
		return 450;
	}

	@Range(min = 10, max = 900)
	@ConfigItem(
		keyName = "anticipationTimeoutSeconds",
		name = "Reel spin timeout (s)",
		description = "How long a raid chest / Olm anticipation reel keeps free-spinning while waiting for the loot "
			+ "before it gives up (e.g. if you leave the raid without opening the chest).",
		section = advanced,
		position = 4
	)
	default int anticipationTimeoutSeconds()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "minItemValue",
		name = "Min item value (gp)",
		description = "Treat dropped items below this value as misses (they roll to 'Nothing'). 0 = count everything",
		section = advanced,
		position = 5
	)
	default int minItemValue()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "hideRareDropTable",
		name = "Hide rare drop table",
		description = "Leave the shared rare/gem/mega drop table (uncut gems, half keys, deep rune/dragon drops) out of "
			+ "the reel so the monster's own drops and uniques stand out. Items you actually receive still land.",
		section = advanced,
		position = 6
	)
	default boolean hideRareDropTable()
	{
		return true;
	}

	@ConfigItem(
		keyName = "wikiDropTables",
		name = "Live wiki fallback",
		description = "<html>Drop tables ship bundled with the plugin, so full reels (every possible drop, roll counts, "
			+ "always-drop filtering and 'Nothing' results) work out of the box with no network request.<br>"
			+ "Turn this on to also fetch tables live from the OSRS Wiki for monsters missing from the bundled "
			+ "snapshot (e.g. brand-new content).<br>"
			+ "<i>Each monster is fetched once and cached.</i></html>",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = advanced,
		position = 7
	)
	default boolean wikiDropTables()
	{
		return false;
	}

	// ---- Rarity colours ----

	@ConfigItem(
		keyName = "colourAlways",
		name = "Always",
		description = "Items dropped on every kill (100%)",
		section = rarityColours,
		position = 0
	)
	default Color colourAlways()
	{
		return new Color(0xAF, 0xEE, 0xEE);
	}

	@ConfigItem(
		keyName = "colourCommon",
		name = "Common",
		description = "Drop chance of 1/25 or better",
		section = rarityColours,
		position = 1
	)
	default Color colourCommon()
	{
		return new Color(0x56, 0xE1, 0x56);
	}

	@ConfigItem(
		keyName = "colourUncommon",
		name = "Uncommon",
		description = "Drop chance between 1/25 and 1/100",
		section = rarityColours,
		position = 2
	)
	default Color colourUncommon()
	{
		return new Color(0xFF, 0xED, 0x4C);
	}

	@ConfigItem(
		keyName = "colourRare",
		name = "Rare",
		description = "Drop chance between 1/100 and 1/1000",
		section = rarityColours,
		position = 3
	)
	default Color colourRare()
	{
		return new Color(0xFF, 0x86, 0x3C);
	}

	@ConfigItem(
		keyName = "colourVeryRare",
		name = "Very rare",
		description = "Drop chance rarer than 1/1000",
		section = rarityColours,
		position = 4
	)
	default Color colourVeryRare()
	{
		return new Color(0xFF, 0x62, 0x62);
	}

	@ConfigItem(
		keyName = "colourVaries",
		name = "Varies / random",
		description = "Items whose drop chance varies or is random",
		section = rarityColours,
		position = 5
	)
	default Color colourVaries()
	{
		return new Color(0xFF, 0xA3, 0xFF);
	}
}
