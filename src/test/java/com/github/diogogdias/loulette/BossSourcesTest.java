package com.github.diogogdias.loulette;

import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BossSourcesTest
{
	@Test
	public void recognisesBossesAndEncounterNpcNames()
	{
		for (String name : new String[]{
			"Zulrah", "General Graardor", "Kree'arra", "K'ril Tsutsaroth", "Duke Sucellus", "Zalcano",
			"The Nightmare", "Phosani's Nightmare", "Dawn", "Dusk", "Branda the fire queen",
			"Eldric the ice king", "Crystalline Hunllef", "Corrupted Hunllef", "Vet'ion Reborn", "Great Olm"
		})
		{
			assertTrue(name, BossSources.isBoss(name));
		}
	}

	@Test
	public void excludesNormalMonstersBossMinionsAndHarvests()
	{
		for (String name : new String[]{
			"Goblin", "Abyssal demon", "Sergeant Strongstack", "Tz-Kih", "Scavenger beast",
			"Vorkath spawn", "Giant kraken", "null", "", "Zulrah's minion"
		})
		{
			assertFalse(name, BossSources.isBoss(name));
		}
		assertFalse(BossSources.isBoss(null));
	}

	@Test
	public void handlesCaseTagsAndNonBreakingSpaces()
	{
		assertTrue(BossSources.isBoss(" <col=ff0000>THE NIGHTMARE</col> "));
		assertTrue(BossSources.isBoss("General\u00a0Graardor"));
	}

	@Test
	public void normalisationDoesNotDependOnTheSystemLocale()
	{
		Locale previous = Locale.getDefault();
		try
		{
			Locale.setDefault(new Locale("tr", "TR"));
			assertTrue(BossSources.isBoss("GIANT MOLE"));
		}
		finally
		{
			Locale.setDefault(previous);
		}
	}
}
