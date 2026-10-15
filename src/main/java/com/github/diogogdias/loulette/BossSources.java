package com.github.diogogdias.loulette;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.util.Text;

/** Boss names from RuneLite's maintained hiscore list, plus NPC names that differ from the encounter name. */
final class BossSources
{
	private static final Set<String> NAMES = new HashSet<>();

	static
	{
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() == HiscoreSkillType.BOSS)
			{
				NAMES.add(normalise(skill.getName()));
			}
		}
		NAMES.addAll(Arrays.asList(
			// Encounter names on the hiscores, individual NPC names in loot/death events.
			"dawn", "dusk",                         // Grotesque Guardians
			"branda the fire queen", "eldric the ice king", // The Royal Titans
			"crystalline hunllef", "corrupted hunllef",     // The (Corrupted) Gauntlet
			"vet'ion reborn",                       // Vet'ion's second form
			"great olm"                             // Starts the CoX reward-chest anticipation reel
		));
	}

	private BossSources()
	{
	}

	static boolean isBoss(String name)
	{
		return name != null && NAMES.contains(normalise(name));
	}

	private static String normalise(String name)
	{
		final String lower = Text.removeTags(name).replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
		// The hiscores call the NPC "The Nightmare" simply "Nightmare".
		return lower.startsWith("the ") ? lower.substring(4) : lower;
	}
}
