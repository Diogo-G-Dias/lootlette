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

	ResolvedTable(List<Integer> pool, Map<Integer, Color> palette, Set<Integer> alwaysIds, int mainRolls)
	{
		this.pool = pool;
		this.palette = palette;
		this.alwaysIds = alwaysIds;
		this.mainRolls = mainRolls;
	}
}
