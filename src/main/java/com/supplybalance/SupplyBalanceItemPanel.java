package com.supplybalance;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

class SupplyBalanceItemPanel extends JPanel
{
	private static final Dimension ROW_SIZE = new Dimension(0, 62);

	private final JLabel bankedLabel = new JLabel();
	private final JLabel netLabel = new JLabel();

	SupplyBalanceItemPanel(
		ItemManager itemManager,
		int itemId,
		String itemName,
		Runnable onSelected)
	{
		super(new BorderLayout(8, 0));

		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 3, 0, ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(7, 7, 7, 7)));
		setPreferredSize(ROW_SIZE);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_SIZE.height));
		setToolTipText("View " + itemName + " history");

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(36, 36));
		iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		AsyncBufferedImage image = itemManager.getImage(itemId);
		if (image != null)
		{
			image.addTo(iconLabel);
		}
		add(iconLabel, BorderLayout.WEST);

		JPanel textPanel = new JPanel(new GridLayout(2, 1));
		textPanel.setOpaque(false);

		JLabel nameLabel = new JLabel(itemName);
		nameLabel.setForeground(ColorScheme.TEXT_COLOR);
		textPanel.add(nameLabel);

		JPanel valuesPanel = new JPanel(new GridLayout(1, 2, 6, 0));
		valuesPanel.setOpaque(false);
		bankedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		valuesPanel.add(bankedLabel);
		valuesPanel.add(netLabel);
		textPanel.add(valuesPanel);
		add(textPanel, BorderLayout.CENTER);

		JLabel arrowLabel = new JLabel("\u203A", SwingConstants.CENTER);
		arrowLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(arrowLabel, BorderLayout.EAST);

		MouseAdapter mouseAdapter = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (SwingUtilities.isLeftMouseButton(event))
				{
					onSelected.run();
				}
			}

			@Override
			public void mouseEntered(MouseEvent event)
			{
				setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		};
		registerMouseHandler(this, mouseAdapter);
	}

	void update(int bankedQuantity, long net)
	{
		bankedLabel.setText("Banked " + QuantityFormatter.quantityToStackSize(bankedQuantity));
		netLabel.setText("Net " + formatSigned(net));

		if (net > 0)
		{
			netLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
		else if (net < 0)
		{
			netLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		}
		else
		{
			netLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
	}

	private static void registerMouseHandler(Component component, MouseAdapter mouseAdapter)
	{
		component.addMouseListener(mouseAdapter);
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				registerMouseHandler(child, mouseAdapter);
			}
		}
	}

	private static String formatSigned(long quantity)
	{
		String formatted = QuantityFormatter.quantityToStackSize(quantity);
		return quantity > 0 ? "+" + formatted : formatted;
	}
}
