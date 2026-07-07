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
	/** Item id -> drop chance (1/128 = 0.0078); NaN = varies, <= 0 = unknown. Used to rank the rarest hit. */
	private final Map<Integer, Double> idChance;
	private final Set<Integer> alwaysIds;
	/** Item ids tagged as the monster's uniques (wiki "Uniques" drop section); prioritised when the reel cap trims. */
	private final Set<Integer> uniqueIds;
	private final int mainRolls;
	/** True when every drop is guaranteed ("Always"): nothing is actually rolled, so no reel should spin. */
	private final boolean alwaysOnly;

	ResolvedTable(List<Integer> pool, Map<Integer, Color> palette, Map<Integer, Double> idChance,
		Set<Integer> alwaysIds, Set<Integer> uniqueIds, int mainRolls, boolean alwaysOnly)
	{
		this.pool = pool;
		this.palette = palette;
		this.idChance = idChance;
		this.alwaysIds = alwaysIds;
		this.uniqueIds = uniqueIds;
		this.mainRolls = mainRolls;
		this.alwaysOnly = alwaysOnly;
	}
}
