package com.github.diogogdias.loulette;

/** Which tile of an NPC's footprint the vertical reel anchors to. */
public enum VerticalAnchor
{
	SOUTH_WEST("South-west tile"),
	CENTER("Centre tile (3x3+)"),
	PLAYER("On top of player");

	private final String label;

	VerticalAnchor(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
