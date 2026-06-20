package com.github.diogogdias.loulette;

import java.util.List;
import lombok.Getter;

/**
 * One vertical slot reel. It free-spins at a constant speed until {@link #land} is called, then
 * eases out to a stop with the target symbol centred. The target may be a real item id or the
 * {@link LoulettePlugin#NOTHING_ID} "miss" symbol.
 */
@Getter
class SlotReel
{
	private final List<Integer> symbols;
	private final long spinStartMs;
	private final double freeSpeed; // symbols per second

	private boolean landing;
	private long landStartMs;
	private long landDurationMs;
	private double landFromPos;
	private double landToPos;
	private int targetItemId = -1;
	private int quantity = 1;

	SlotReel(List<Integer> symbols, long spinStartMs, double freeSpeed)
	{
		this.symbols = symbols;
		this.spinStartMs = spinStartMs;
		this.freeSpeed = freeSpeed;
	}

	void land(long now, int targetItemId, int quantity, long durationMs, int minExtraLoops)
	{
		final int size = symbols.size();
		final int targetIndex = Math.max(0, symbols.indexOf(targetItemId));
		final double cur = position(now);

		long k = (long) Math.floor(cur) + (long) minExtraLoops * size;
		while ((((k % size) + size) % size) != targetIndex)
		{
			k++;
		}

		this.landFromPos = cur;
		this.landToPos = k;
		this.landStartMs = now;
		this.landDurationMs = durationMs;
		this.targetItemId = targetItemId;
		this.quantity = quantity;
		this.landing = true;
	}

	double position(long now)
	{
		if (!landing)
		{
			return freeSpeed * (now - spinStartMs) / 1000.0;
		}
		final double t = clamp((double) (now - landStartMs) / landDurationMs);
		final double eased = 1 - Math.pow(1 - t, 3); // easeOutCubic
		return landFromPos + (landToPos - landFromPos) * eased;
	}

	boolean landed(long now)
	{
		return landing && now >= landStartMs + landDurationMs;
	}

	boolean expired(long now, long lingerMs, long fadeMs)
	{
		return landing && now > landStartMs + landDurationMs + lingerMs + fadeMs;
	}

	/**
	 * Opacity for drawing: 1.0 while spinning and through the full {@code lingerMs} result hold, then ramping
	 * down to 0 over the following {@code fadeMs} so the reel fades out instead of blinking off.
	 */
	double fadeAlpha(long now, long lingerMs, long fadeMs)
	{
		if (!landed(now))
		{
			return 1.0;
		}
		final long sinceResult = now - (landStartMs + landDurationMs);
		if (sinceResult <= lingerMs)
		{
			return 1.0;
		}
		if (fadeMs <= 0)
		{
			return 0.0;
		}
		final long fadeElapsed = sinceResult - lingerMs;
		return fadeElapsed >= fadeMs ? 0.0 : 1.0 - (double) fadeElapsed / fadeMs;
	}

	private static double clamp(double v)
	{
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
