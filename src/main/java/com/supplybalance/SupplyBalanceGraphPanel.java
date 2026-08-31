package com.supplybalance;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

class SupplyBalanceGraphPanel extends JPanel
{
	private static final Color LINE_COLOR = new Color(80, 145, 210);
	private static final Color GRID_COLOR = new Color(58, 68, 82);
	private static final int LEFT_PADDING = 38;
	private static final int RIGHT_PADDING = 8;
	private static final int TOP_PADDING = 12;
	private static final int BOTTOM_PADDING = 24;

	private List<SupplyBalanceHistoryPoint> points = new ArrayList<>();

	SupplyBalanceGraphPanel()
	{
		setOpaque(true);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(0, 160));
	}

	void setData(List<SupplyBalanceHistoryPoint> points)
	{
		this.points = new ArrayList<>(points);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);

		Graphics2D graphics2D = (Graphics2D) graphics.create();
		try
		{
			graphics2D.setRenderingHint(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

			if (points.isEmpty())
			{
				drawEmptyMessage(graphics2D);
				return;
			}

			drawGraph(graphics2D);
		}
		finally
		{
			graphics2D.dispose();
		}
	}

	private void drawEmptyMessage(Graphics2D graphics2D)
	{
		String message = "No quantity history yet";
		FontMetrics metrics = graphics2D.getFontMetrics();
		graphics2D.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		graphics2D.drawString(
			message,
			Math.max(4, (getWidth() - metrics.stringWidth(message)) / 2),
			getHeight() / 2);
	}

	private void drawGraph(Graphics2D graphics2D)
	{
		int plotWidth = getWidth() - LEFT_PADDING - RIGHT_PADDING;
		int plotHeight = getHeight() - TOP_PADDING - BOTTOM_PADDING;
		if (plotWidth <= 0 || plotHeight <= 0)
		{
			return;
		}

		long minimum = points.get(0).getQuantity();
		long maximum = minimum;
		for (SupplyBalanceHistoryPoint point : points)
		{
			minimum = Math.min(minimum, point.getQuantity());
			maximum = Math.max(maximum, point.getQuantity());
		}

		long displayMinimum = minimum;
		long displayMaximum = maximum;
		if (displayMinimum == displayMaximum)
		{
			displayMinimum = Math.max(0, displayMinimum - 1);
			displayMaximum++;
		}

		FontMetrics metrics = graphics2D.getFontMetrics();
		graphics2D.setColor(GRID_COLOR);
		for (int line = 0; line <= 2; line++)
		{
			int y = TOP_PADDING + line * plotHeight / 2;
			graphics2D.drawLine(LEFT_PADDING, y, LEFT_PADDING + plotWidth, y);

			long value = displayMaximum
				- Math.round((displayMaximum - displayMinimum) * (line / 2.0));
			String label = QuantityFormatter.quantityToStackSize(value);
			graphics2D.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			graphics2D.drawString(label, LEFT_PADDING - metrics.stringWidth(label) - 4, y + 4);
			graphics2D.setColor(GRID_COLOR);
		}

		long firstTimestamp = points.get(0).getTimestamp();
		long lastTimestamp = points.get(points.size() - 1).getTimestamp();
		int previousX = 0;
		int previousY = 0;

		graphics2D.setColor(LINE_COLOR);
		graphics2D.setStroke(new BasicStroke(2.0f));
		for (int index = 0; index < points.size(); index++)
		{
			SupplyBalanceHistoryPoint point = points.get(index);
			int x = calculateX(index, point.getTimestamp(), firstTimestamp, lastTimestamp, plotWidth);
			int y = TOP_PADDING + (int) Math.round(
				(displayMaximum - point.getQuantity())
					/ (double) (displayMaximum - displayMinimum) * plotHeight);

			if (index > 0)
			{
				graphics2D.drawLine(previousX, previousY, x, y);
			}

			graphics2D.fillOval(x - 2, y - 2, 5, 5);
			previousX = x;
			previousY = y;
		}

		graphics2D.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		graphics2D.drawString("Older", LEFT_PADDING, getHeight() - 7);
		String latest = "Latest";
		graphics2D.drawString(
			latest,
			LEFT_PADDING + plotWidth - metrics.stringWidth(latest),
			getHeight() - 7);
	}

	private int calculateX(
		int index,
		long timestamp,
		long firstTimestamp,
		long lastTimestamp,
		int plotWidth)
	{
		if (points.size() == 1)
		{
			return LEFT_PADDING + plotWidth / 2;
		}

		if (firstTimestamp == lastTimestamp)
		{
			return LEFT_PADDING + index * plotWidth / (points.size() - 1);
		}

		return LEFT_PADDING + (int) Math.round(
			(timestamp - firstTimestamp) / (double) (lastTimestamp - firstTimestamp) * plotWidth);
	}
}
