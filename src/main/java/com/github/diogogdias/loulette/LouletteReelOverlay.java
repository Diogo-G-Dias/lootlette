package com.github.diogogdias.loulette;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** CS:GO case-opening style: horizontal reels scrolling under a fixed centre ticker, fixed at the top centre. */
class LouletteReelOverlay extends Overlay
{
	private static final int CELL = 40;
	private static final int STRIP_W = 280;
	private static final int ROW_H = 48;
	private static final int PAD = 6;
	private static final int HEADER = 15;
	private static final int ROW_GAP = 3;

	private static final Color BG = new Color(20, 20, 20, 225);
	private static final Color WINDOW_BG = new Color(10, 10, 10, 255);
	private static final Color FRAME = new Color(90, 75, 35);
	private static final Color TICKER = new Color(255, 215, 0);
	private static final Color HEADER_TEXT = new Color(255, 215, 0);
	private static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);

	private final LoulettePlugin plugin;
	private final LouletteConfig config;
	private final ItemManager itemManager;

	@Inject
	LouletteReelOverlay(LoulettePlugin plugin, LouletteConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		// When the global toggle is off, only raids chests (forceHorizontal) use this overlay.
		final boolean all = config.horizontalReel();
		final List<ActiveRoll> rolls = new ArrayList<>();
		for (ActiveRoll roll : plugin.getActiveRolls())
		{
			if (all || roll.isForceHorizontal())
			{
				rolls.add(roll);
			}
		}

		int groups = 0;
		int rows = 0;
		for (ActiveRoll roll : rolls)
		{
			if (!roll.getReels().isEmpty())
			{
				groups++;
				rows += roll.getReels().size();
			}
		}
		if (rows == 0)
		{
			return null;
		}

		final double scale = config.slotScale() / 100.0;
		final int cell = (int) Math.round(CELL * scale);
		final int rowH = (int) Math.round(ROW_H * scale);

		final long now = System.currentTimeMillis();
		final int width = STRIP_W + PAD * 2;
		final int height = PAD * 2 + groups * HEADER + rows * (rowH + ROW_GAP);

		g.setColor(BG);
		g.fillRoundRect(0, 0, width, height, 8, 8);
		g.setColor(FRAME);
		g.setStroke(new BasicStroke(1));
		g.drawRoundRect(0, 0, width, height, 8, 8);

		g.setFont(HEADER_FONT);
		final FontMetrics fm = g.getFontMetrics();

		int y = PAD;
		for (ActiveRoll roll : rolls)
		{
			final List<SlotReel> reels = roll.getReels();
			if (reels.isEmpty())
			{
				continue;
			}
			final String header = (reels.size() == 1 ? "1 roll" : reels.size() + " rolls");
			g.setColor(HEADER_TEXT);
			g.drawString(header, PAD + (STRIP_W - fm.stringWidth(header)) / 2, y + HEADER - 3);
			y += HEADER;

			for (SlotReel reel : reels)
			{
				drawStrip(g, reel, roll.getPalette(), now, PAD, y, STRIP_W, rowH, cell, scale);
				y += rowH + ROW_GAP;
			}
		}

		return new Dimension(width, height);
	}

	private void drawStrip(Graphics2D g, SlotReel reel, Map<Integer, Color> palette, long now, int x, int y, int w,
		int h, int cell, double scale)
	{
		final Rectangle strip = new Rectangle(x, y, w, h);
		g.setColor(WINDOW_BG);
		g.fill(strip);

		final double pos = reel.position(now);
		final int centerX = x + w / 2;
		final int rowCenterY = y + h / 2;
		final List<Integer> symbols = reel.getSymbols();
		final int size = symbols.size();
		final boolean landed = reel.landed(now);
		final int half = w / (2 * cell) + 2;

		final Shape oldClip = g.getClip();
		g.setClip(strip);
		final long base = (long) Math.floor(pos);
		for (int k = -half; k <= half; k++)
		{
			final long s = base + k;
			final int idx = (int) (((s % size) + size) % size);
			final int id = symbols.get(idx);
			final int cellX = (int) Math.round(centerX + (s - pos) * cell);
			Reels.glow(g, id, palette, cellX, rowCenterY, false, scale);
			Reels.symbol(g, itemManager, id, 1, cellX, rowCenterY, scale);
		}
		if (landed)
		{
			final int id = reel.getTargetItemId();
			Reels.glow(g, id, palette, centerX, rowCenterY, true, scale);
			Reels.symbol(g, itemManager, id, reel.getQuantity(), centerX, rowCenterY, scale);
		}
		g.setClip(oldClip);

		if (landed)
		{
			g.setColor(Reels.colourFor(reel.getTargetItemId(), palette));
			g.setStroke(new BasicStroke(2));
			g.drawRect(centerX - cell / 2 + 1, y + 2, cell - 2, h - 4);
		}

		// Centre ticker (CS:GO marker)
		g.setColor(TICKER);
		g.setStroke(new BasicStroke(2));
		g.drawLine(centerX, y, centerX, y + h);
		g.fillPolygon(new Polygon(
			new int[]{centerX - 5, centerX + 5, centerX}, new int[]{y, y, y + 7}, 3));
		g.fillPolygon(new Polygon(
			new int[]{centerX - 5, centerX + 5, centerX}, new int[]{y + h, y + h, y + h - 7}, 3));

		g.setColor(FRAME);
		g.setStroke(new BasicStroke(1));
		g.draw(strip);
	}
}
