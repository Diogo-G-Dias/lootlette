package com.github.diogogdias.loulette;

import java.awt.Color;

/** Maps an OSRS drop chance onto the OSRS Wiki's drop-table rarity colours. */
final class Rarity
{
	static final Color NEUTRAL = new Color(150, 150, 150); // unknown rarity (wiki default grey)

	private Rarity()
	{
	}

	/** @param chance drop probability (1/128 = 0.0078); NaN = varies/random; <= 0 = unknown. */
	static Color colour(double chance, LouletteConfig config)
	{
		if (Double.isNaN(chance))
		{
			return config.colourVaries();
		}
		if (chance <= 0)
		{
			return NEUTRAL;
		}
		if (chance >= 1.0)
		{
			return config.colourAlways();
		}
		if (chance >= 1 / 25.0)
		{
			return config.colourCommon();
		}
		if (chance >= 1 / 100.0)
		{
			return config.colourUncommon();
		}
		if (chance >= 1 / 1000.0)
		{
			return config.colourRare();
		}
		return config.colourVeryRare();
	}
}
