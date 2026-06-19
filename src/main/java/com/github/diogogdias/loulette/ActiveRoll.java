package com.github.diogogdias.loulette;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;

/** A roll anchored to the world tile where an NPC died, holding one reel per loot roll. */
@Getter
class ActiveRoll
{
	private final int npcIndex;
	private final WorldPoint location;
	private final long spinStartMs;
	private final String monster;

	private final List<SlotReel> reels = new CopyOnWriteArrayList<>();
	private boolean finalised;

	// Raids chests have no world tile to anchor to, so they always render on the horizontal top-centre reel.
	@Setter
	private boolean forceHorizontal;

	// A raids-chest reel that starts free-spinning when you enter the reward room, before the chest is opened.
	// It lingers on a longer (2x hold) timeout instead of the normal pre-spin timeout.
	@Setter
	private boolean anticipation;

	// item id -> CS:GO rarity-grade colour for this monster's table
	@Setter
	private Map<Integer, Color> palette = Collections.emptyMap();

	ActiveRoll(int npcIndex, WorldPoint location, long spinStartMs, String monster)
	{
		this.npcIndex = npcIndex;
		this.location = location;
		this.spinStartMs = spinStartMs;
		this.monster = monster;
	}

	void setReels(List<SlotReel> newReels)
	{
		reels.clear();
		reels.addAll(newReels);
		finalised = true;
	}

	boolean done(long now, long lingerMs)
	{
		if (reels.isEmpty())
		{
			return false;
		}
		for (SlotReel reel : reels)
		{
			if (!reel.expired(now, lingerMs))
			{
				return false;
			}
		}
		return true;
	}
}
