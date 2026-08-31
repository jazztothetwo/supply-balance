package com.supplybalance;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

public class SupplyBalancePanel extends PluginPanel
{
	private static final String OVERVIEW_CARD = "overview";
	private static final String DETAIL_CARD = "detail";

	private final ItemManager itemManager;
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cards = new JPanel(cardLayout);
	private final JPanel itemListPanel = new JPanel();
	private final JLabel emptyLabel = new JLabel("No tracked changes yet", SwingConstants.CENTER);
	private final JLabel detailTitle = new JLabel("", SwingConstants.CENTER);
	private final JLabel detailStats = new JLabel("", SwingConstants.CENTER);
	private final SupplyBalanceGraphPanel graphPanel = new SupplyBalanceGraphPanel();
	private final Map<Integer, SupplyBalanceItemPanel> itemPanels = new LinkedHashMap<>();
	private final Map<Integer, ItemData> itemData = new HashMap<>();

	private Integer selectedItemId;

	@Inject
	public SupplyBalancePanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		cards.setOpaque(false);
		cards.add(createOverviewPanel(), OVERVIEW_CARD);
		cards.add(createDetailPanel(), DETAIL_CARD);
		add(cards, BorderLayout.CENTER);

		showEmptyState();
		cardLayout.show(cards, OVERVIEW_CARD);
	}

	public void updateItem(
		int itemId,
		String itemName,
		int bankedQuantity,
		long inflow,
		long outflow,
		long net,
		List<SupplyBalanceHistoryPoint> history)
	{
		SwingUtilities.invokeLater(() ->
		{
			ItemData data = new ItemData(itemName, bankedQuantity, inflow, outflow, net, history);
			itemData.put(itemId, data);

			SupplyBalanceItemPanel itemPanel = itemPanels.get(itemId);
			if (itemPanel == null)
			{
				if (itemPanels.isEmpty())
				{
					itemListPanel.remove(emptyLabel);
				}

				itemPanel = new SupplyBalanceItemPanel(
					itemManager,
					itemId,
					itemName,
					() -> showDetails(itemId));
				itemPanels.put(itemId, itemPanel);
				itemListPanel.add(itemPanel);
			}

			itemPanel.update(bankedQuantity, net);

			if (selectedItemId != null && selectedItemId == itemId)
			{
				updateDetailView(itemId);
			}

			itemListPanel.revalidate();
			itemListPanel.repaint();
		});
	}

	public void reset()
	{
		SwingUtilities.invokeLater(() ->
		{
			selectedItemId = null;
			itemPanels.clear();
			itemData.clear();
			graphPanel.setData(new ArrayList<>());
			showEmptyState();
			cardLayout.show(cards, OVERVIEW_CARD);
		});
	}

	private JPanel createOverviewPanel()
	{
		JPanel overviewPanel = new JPanel(new BorderLayout(0, 8));
		overviewPanel.setOpaque(false);

		JLabel heading = new JLabel("Tracked supplies");
		heading.setForeground(Color.WHITE);
		heading.setBorder(new EmptyBorder(2, 2, 2, 2));
		overviewPanel.add(heading, BorderLayout.NORTH);

		itemListPanel.setLayout(new BoxLayout(itemListPanel, BoxLayout.Y_AXIS));
		itemListPanel.setOpaque(false);
		overviewPanel.add(itemListPanel, BorderLayout.CENTER);

		return overviewPanel;
	}

	private JPanel createDetailPanel()
	{
		JPanel detailPanel = new JPanel(new BorderLayout(0, 8));
		detailPanel.setOpaque(false);

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setOpaque(false);

		JButton backButton = new JButton("Back");
		backButton.setFocusable(false);
		backButton.addActionListener(event ->
		{
			selectedItemId = null;
			cardLayout.show(cards, OVERVIEW_CARD);
		});

		detailTitle.setForeground(Color.WHITE);
		header.add(backButton, BorderLayout.WEST);
		header.add(detailTitle, BorderLayout.CENTER);
		detailPanel.add(header, BorderLayout.NORTH);

		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.setOpaque(false);
		content.add(graphPanel, BorderLayout.CENTER);

		detailStats.setOpaque(true);
		detailStats.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailStats.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			new EmptyBorder(8, 4, 8, 4)));
		content.add(detailStats, BorderLayout.SOUTH);

		detailPanel.add(content, BorderLayout.CENTER);
		return detailPanel;
	}

	private void showDetails(int itemId)
	{
		selectedItemId = itemId;
		updateDetailView(itemId);
		cardLayout.show(cards, DETAIL_CARD);
	}

	private void updateDetailView(int itemId)
	{
		ItemData data = itemData.get(itemId);
		if (data == null)
		{
			return;
		}

		detailTitle.setText(data.itemName);
		detailStats.setText(String.format(
			"<html><center>Banked: %s<br>In: %s&nbsp;&nbsp; Out: %s&nbsp;&nbsp; Net: %s</center></html>",
			QuantityFormatter.quantityToStackSize(data.bankedQuantity),
			QuantityFormatter.quantityToStackSize(data.inflow),
			QuantityFormatter.quantityToStackSize(data.outflow),
			formatSigned(data.net)));
		graphPanel.setData(data.history);
	}

	private void showEmptyState()
	{
		itemListPanel.removeAll();
		emptyLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
		emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		itemListPanel.add(emptyLabel);
		itemListPanel.revalidate();
		itemListPanel.repaint();
	}

	private static String formatSigned(long quantity)
	{
		String formatted = QuantityFormatter.quantityToStackSize(quantity);
		return quantity > 0 ? "+" + formatted : formatted;
	}

	private static class ItemData
	{
		private final String itemName;
		private final int bankedQuantity;
		private final long inflow;
		private final long outflow;
		private final long net;
		private final List<SupplyBalanceHistoryPoint> history;

		private ItemData(
			String itemName,
			int bankedQuantity,
			long inflow,
			long outflow,
			long net,
			List<SupplyBalanceHistoryPoint> history)
		{
			this.itemName = itemName;
			this.bankedQuantity = bankedQuantity;
			this.inflow = inflow;
			this.outflow = outflow;
			this.net = net;
			this.history = new ArrayList<>(history);
		}
	}
}
