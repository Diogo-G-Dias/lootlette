package com.github.diogogdias.loulette;

import lombok.Getter;

/** One line from a monster's wiki drop table: the item, whether it's guaranteed, its roll count and drop chance. */
@Getter
class DropEntry
{
	private final String name;
	private final boolean always;
	private final int rolls;
	private final double chance; // drop probability, e.g. 1/128 = 0.0078; <= 0 means unknown

	DropEntry(String name, boolean always, int rolls, double chance)
	{
		this.name = name;
		this.always = always;
		this.rolls = rolls;
		this.chance = chance;
	}
}
