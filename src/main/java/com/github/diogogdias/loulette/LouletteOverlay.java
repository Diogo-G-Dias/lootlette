package com.github.diogogdias.loulette;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** World-anchored vertical reel cluster drawn over the tile where each monster dies. */
class LouletteOverlay extends Overlay
{
	private static final int CELL_W = 40;
	private static final int CELL_H = 36;
	private static final int VISIBLE_ROWS = 3;
	private static final int GAP = 4;
	private static final int PAD = 4;
	private static final int HEADER = 14;
	private static final int Z_OFFSET = 150;

	private static final Color BG = new Color(20, 20, 20, 215);
	private static final Color WINDOW_BG = new Color(10, 10, 10, 255);
	private static final Color FRAME = new Color(90, 75, 35);
	private static final Color CENTER_LINE = new Color(255, 255, 255, 55);
	private static final Color HEADER_TEXT = new Color(255, 215, 0);
	private static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);

	private final Client client;
	private final LoulettePlugin plugin;
	private final LouletteConfig config;
	private final ItemManager itemManager;

	@Inject
	LouletteOverlay(Client client, LoulettePlugin plugin, LouletteConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		final boolean horizontalMode = config.horizontalReel();
		final List<ActiveRoll> rolls = plugin.getActiveRolls();
		final long now = System.currentTimeMillis();
		for (ActiveRoll roll : rolls)
		{
			if (roll.isForceHorizontal())
			{
				continue;
			}
			// In global horizontal mode the top-centre overlay handles everything, except object-anchored chest
			// reels (forceVertical) which must stay over their tile.
			if (horizontalMode && !roll.isForceVertical())
			{
				continue;
			}
			drawCluster(g, roll, now);
		}
		return null;
	}

	private void drawCluster(Graphics2D g, ActiveRoll roll, long now)
	{
		final List<SlotReel> reels = roll.getReels();
		if (reels.isEmpty() || roll.getLocation() == null)
		{
			return;
		}

		final LocalPoint lp = LocalPoint.fromWorld(client, roll.getLocation());
		if (lp == null)
		{
			return;
		}
		final Point anchor = Perspective.getCanvasTextLocation(client, g, lp, "", Z_OFFSET);
		if (anchor == null)
		{
			return;
		}

		final double scale = config.slotScale() / 100.0;
		final int cellW = (int) Math.round(CELL_W * scale);
		final int cellH = (int) Math.round(CELL_H * scale);

		final int n = reels.size();
		final int windowH = VISIBLE_ROWS * cellH;
		final int width = PAD * 2 + n * cellW + (n - 1) * GAP;
		final int height = HEADER + PAD * 2 + windowH;
		final int x0 = anchor.getX() - width / 2;
		final int y0 = anchor.getY() - height;

		g.setColor(BG);
		g.fillRoundRect(x0, y0, width, height, 8, 8);
		g.setColor(FRAME);
		g.setStroke(new BasicStroke(1));
		g.drawRoundRect(x0, y0, width, height, 8, 8);

		final String header = (n == 1 ? "1 roll" : n + " rolls");
		g.setFont(HEADER_FONT);
		final FontMetrics fm = g.getFontMetrics();
		g.setColor(HEADER_TEXT);
		g.drawString(header, x0 + (width - fm.stringWidth(header)) / 2, y0 + HEADER - 2);

		final Map<Integer, Color> palette = roll.getPalette();
		final Shape oldClip = g.getClip();
		final int windowTop = y0 + HEADER + PAD;
		for (int r = 0; r < n; r++)
		{
			final int x = x0 + PAD + r * (cellW + GAP);
			drawReel(g, reels.get(r), palette, now, x, windowTop, windowH, cellW, cellH, scale);
		}
		g.setClip(oldClip);
	}

	private void drawReel(Graphics2D g, SlotReel reel, Map<Integer, Color> palette, long now, int x, int windowTop,
		int windowH, int cellW, int cellH, double scale)
	{
		final Rectangle window = new Rectangle(x, windowTop, cellW, windowH);

		g.setColor(WINDOW_BG);
		g.fill(window);

		final double pos = reel.position(now);
		final int cellCenterX = x + cellW / 2;
		final int centerRowY = windowTop + windowH / 2;
		final List<Integer> symbols = reel.getSymbols();
		final int size = symbols.size();
		final boolean landed = reel.landed(now);

		g.setClip(window);
		final long base = (long) Math.floor(pos);
		for (int k = -(VISIBLE_ROWS / 2) - 1; k <= (VISIBLE_ROWS / 2) + 1; k++)
		{
			final long s = base + k;
			final int idx = (int) (((s % size) + size) % size);
			final int id = symbols.get(idx);
			final int yCenter = (int) Math.round(centerRowY + (s - pos) * cellH);
			Reels.glow(g, id, palette, cellCenterX, yCenter, false, scale);
			Reels.symbol(g, itemManager, id, 1, cellCenterX, yCenter, scale);
		}
		if (landed)
		{
			final int id = reel.getTargetItemId();
			Reels.glow(g, id, palette, cellCenterX, centerRowY, true, scale);
			Reels.symbol(g, itemManager, id, reel.getQuantity(), cellCenterX, centerRowY, scale);
		}
		g.setClip(null);

		if (landed)
		{
			g.setColor(Reels.colourFor(reel.getTargetItemId(), palette));
			g.setStroke(new BasicStroke(2));
			g.drawRect(x + 1, centerRowY - cellH / 2 + 1, cellW - 2, cellH - 2);
		}
		else
		{
			g.setColor(CENTER_LINE);
			g.setStroke(new BasicStroke(1));
			g.drawLine(x, centerRowY - cellH / 2, x + cellW, centerRowY - cellH / 2);
			g.drawLine(x, centerRowY + cellH / 2, x + cellW, centerRowY + cellH / 2);
		}

		g.setColor(FRAME);
		g.setStroke(new BasicStroke(1));
		g.draw(window);
	}
}
