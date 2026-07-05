package com.github.diogogdias.loulette;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/** A monster's wiki drop table once resolved to item ids: the reel pool, rarity colours, guaranteed-drop ids and roll count. */
@Getter
class ResolvedTable
{
	private final List<Integer> pool;
	private final Map<Integer, Color> palette;
	private final Set<Integer> alwaysIds;
	private final int mainRolls;
	/** Pet item ids on this table, kept out of {@link #pool} so they only ever land on their own dedicated reel. */
	private final Set<Integer> petIds;
	/** Separate pet rolls (one reel per distinct pet), mirroring OSRS rolling the pet apart from the main table. */
	private final int petRolls;
	/** True when every drop is guaranteed ("Always"): nothing is actually rolled, so no reel should spin. */
	private final boolean alwaysOnly;

	ResolvedTable(List<Integer> pool, Map<Integer, Color> palette, Set<Integer> alwaysIds, int mainRolls,
		Set<Integer> petIds, int petRolls, boolean alwaysOnly)
	{
		this.pool = pool;
		this.palette = palette;
		this.alwaysIds = alwaysIds;
		this.mainRolls = mainRolls;
		this.petIds = petIds;
		this.petRolls = petRolls;
		this.alwaysOnly = alwaysOnly;
	}
}
