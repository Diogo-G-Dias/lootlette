package com.github.diogogdias.loulette;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.util.Map;
import net.runelite.client.game.ItemManager;

/** Shared reel-cell drawing used by both the world overlay and the horizontal top-centre overlay. */
final class Reels
{
	static final int SPIN_RADIUS = 23;
	static final int LAND_RADIUS = 32;

	private static final Color NOTHING = new Color(120, 120, 120);

	private Reels()
	{
	}

	static Color colourFor(int itemId, Map<Integer, Color> palette)
	{
		if (itemId == LoulettePlugin.NOTHING_ID)
		{
			return Rarity.NEUTRAL;
		}
		return palette.getOrDefault(itemId, Rarity.NEUTRAL);
	}

	/** Radial rarity glow behind an item. */
	static void glow(Graphics2D g, int itemId, Map<Integer, Color> palette, int cx, int cy, boolean strong, double scale)
	{
		if (itemId == LoulettePlugin.NOTHING_ID)
		{
			return;
		}
		final Color c = colourFor(itemId, palette);
		final int radius = (int) Math.round((strong ? LAND_RADIUS : SPIN_RADIUS) * scale);
		final int alpha = strong ? 215 : 130;
		final Paint old = g.getPaint();
		g.setPaint(new RadialGradientPaint(
			new Point2D.Float(cx, cy), radius,
			new float[]{0f, 1f},
			new Color[]{
				new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha),
				new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)
			}));
		g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
		g.setPaint(old);
	}

	static void symbol(Graphics2D g, ItemManager itemManager, int itemId, int quantity, int cx, int cy, double scale)
	{
		if (itemId == LoulettePlugin.NOTHING_ID)
		{
			final int r = (int) Math.round(9 * scale);
			g.setColor(NOTHING);
			g.setStroke(new BasicStroke(2));
			g.drawOval(cx - r, cy - r, r * 2, r * 2);
			g.drawLine(cx - r, cy + r, cx + r, cy - r);
			return;
		}
		final Image img = quantity > 1
			? itemManager.getImage(itemId, quantity, true)
			: itemManager.getImage(itemId);
		if (img == null)
		{
			return;
		}
		final int w = img.getWidth(null);
		final int h = img.getHeight(null);
		if (w <= 0 || h <= 0)
		{
			return;
		}
		final int dw = (int) Math.round(w * scale);
		final int dh = (int) Math.round(h * scale);
		g.drawImage(img, cx - dw / 2, cy - dh / 2, dw, dh, null);
	}
}
