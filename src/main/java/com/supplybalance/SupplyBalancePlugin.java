package com.supplybalance;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.ItemComposition;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.config.ConfigManager;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
		name = "Supply Balance",
		description = "Tracks the long-term gain and loss of supplies through your bank",
		tags = {"bank", "supplies", "ironman", "resources", "tracker"}
)
public class SupplyBalancePlugin extends Plugin
{
	private static final String CONFIG_GROUP = "supply-balance";
	private static final String INFLOW_KEY_PREFIX = "inflow.";
	private static final String OUTFLOW_KEY_PREFIX = "outflow.";
	private static final String HISTORY_KEY_PREFIX = "history.";
	private static final int MAX_HISTORY_POINTS = 500;

	private Map<Integer, Integer> previousBankSnapshot;
	private final Map<Integer, Long> totalInflow = new HashMap<>();
	private final Map<Integer, Long> totalOutflow = new HashMap<>();
	private final Map<Integer, List<SupplyBalanceHistoryPoint>> itemHistory = new HashMap<>();
	private final Map<Integer, Integer> pendingHistoryStartQuantities = new HashMap<>();
	private final Map<Integer, Integer> pendingHistoryCurrentQuantities = new HashMap<>();
	private final Map<Integer, Long> pendingHistoryStartTimestamps = new HashMap<>();

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	private SupplyBalancePanel panel;
	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		previousBankSnapshot = null;
		totalInflow.clear();
		totalOutflow.clear();
		itemHistory.clear();
		clearPendingHistory();
		loadAllSavedTotals();

		panel = injector.getInstance(SupplyBalancePanel.class);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

		navigationButton = NavigationButton.builder()
				.tooltip("Supply Balance")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navigationButton);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		previousBankSnapshot = null;
		totalInflow.clear();
		totalOutflow.clear();
		itemHistory.clear();
		clearPendingHistory();
		loadAllSavedTotals();
		if (panel != null)
		{
			panel.reset();
		}
	}

	@Override
	protected void shutDown()
	{
		flushPendingHistory(false);
		clientToolbar.removeNavigation(navigationButton);
		previousBankSnapshot = null;
		log.debug("Supply Balance stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState gameState = gameStateChanged.getGameState();

		if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING)
		{
			flushPendingHistory(false);
			previousBankSnapshot = null;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			flushPendingHistory(true);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK)
		{
			return;
		}
		Map<Integer, Integer> currentBankSnapshot =
				createBankSnapshot(event.getItemContainer());

		if (previousBankSnapshot == null)
		{
			previousBankSnapshot = currentBankSnapshot;
			log.debug("Stored initial bank snapshot containing {} item types",
					previousBankSnapshot.size());
			populatePanelFromSnapshot(currentBankSnapshot);
			return;
		}
		Set<Integer> allItemIds = new HashSet<>(previousBankSnapshot.keySet());
		allItemIds.addAll(currentBankSnapshot.keySet());

		for (int itemId : allItemIds)
		{
			int previousQuantity = previousBankSnapshot.getOrDefault(itemId, 0);
			int currentQuantity = currentBankSnapshot.getOrDefault(itemId, 0);
			int change = currentQuantity - previousQuantity;

			if (change == 0)
			{
				continue;
			}

			if (!totalInflow.containsKey(itemId))
			{
				totalInflow.put(itemId, loadSavedTotal(INFLOW_KEY_PREFIX, itemId));
			}

			if (!totalOutflow.containsKey(itemId))
			{
				totalOutflow.put(itemId, loadSavedTotal(OUTFLOW_KEY_PREFIX, itemId));
			}

			if (change > 0)
			{
				long existingInflow = totalInflow.getOrDefault(itemId, 0L);
				long newInflow = existingInflow + change;
				
				totalInflow.put(itemId, newInflow);
				configManager.setRSProfileConfiguration(
						CONFIG_GROUP,
						INFLOW_KEY_PREFIX + itemId,
						newInflow);
			}
			else
			{
				long amountOut = -change;
				long existingOutFlow = totalOutflow.getOrDefault(itemId, 0L);
				long newOutflow = existingOutFlow + amountOut;

				totalOutflow.put(itemId, newOutflow);
				configManager.setRSProfileConfiguration(
						CONFIG_GROUP,
						OUTFLOW_KEY_PREFIX + itemId,
						newOutflow);
			}

			long inflow = totalInflow.getOrDefault(itemId, 0L);
			long outflow = totalOutflow.getOrDefault(itemId, 0L);
			long net = inflow - outflow;
			queueHistoryChange(itemId, previousQuantity, currentQuantity);
			List<SupplyBalanceHistoryPoint> history = historySnapshot(itemId);

			String itemName = client.getItemDefinition(itemId).getName();
			panel.updateItem(
				itemId,
				itemName,
				currentQuantity,
				inflow,
				outflow,
				net,
				history);
			log.debug("{} ({}) changed by {} | In: {} Out: {} Net: {}",
					itemName, itemId, change, inflow, outflow, net);
        }
		previousBankSnapshot = currentBankSnapshot;
	}

	private Map<Integer, Integer> createBankSnapshot(ItemContainer bank)
	{
		Map<Integer, Integer> snapshot = new HashMap<>();
		for (Item item : bank.getItems())
		{
			int itemID = item.getId();
			int quantity = item.getQuantity();
			if (itemID < 0 || quantity <= 0 || !SupplyRegistry.isTracked(itemID))
			{
				continue;
			}
			ItemComposition itemComposition = client.getItemDefinition(itemID);
			if (itemComposition.getPlaceholderTemplateId() != -1) {
				continue;
			}
			snapshot.put(itemID, quantity);
		}
		return snapshot;
	}

	private void populatePanelFromSnapshot(Map<Integer, Integer> bankSnapshot)
	{
		Set<Integer> trackedItemIds = new HashSet<>(totalInflow.keySet());
		trackedItemIds.addAll(totalOutflow.keySet());
		trackedItemIds.addAll(itemHistory.keySet());

		for (int itemId : trackedItemIds)
		{
			int bankedQuantity = bankSnapshot.getOrDefault(itemId, 0);
			long inflow = totalInflow.getOrDefault(itemId, 0L);
			long outflow = totalOutflow.getOrDefault(itemId, 0L);
			long net = inflow - outflow;
			List<SupplyBalanceHistoryPoint> history = historySnapshot(itemId);
			String itemName = client.getItemDefinition(itemId).getName();

			panel.updateItem(
				itemId,
				itemName,
				bankedQuantity,
				inflow,
				outflow,
				net,
				history);
		}
	}

	private void queueHistoryChange(
		int itemId,
		int previousQuantity,
		int currentQuantity)
	{
		pendingHistoryStartQuantities.putIfAbsent(itemId, previousQuantity);
		pendingHistoryStartTimestamps.putIfAbsent(itemId, System.currentTimeMillis());
		pendingHistoryCurrentQuantities.put(itemId, currentQuantity);
	}

	private void flushPendingHistory(boolean updatePanel)
	{
		if (pendingHistoryCurrentQuantities.isEmpty())
		{
			return;
		}

		long closeTimestamp = System.currentTimeMillis();
		for (int itemId : new HashSet<>(pendingHistoryCurrentQuantities.keySet()))
		{
			int startQuantity = pendingHistoryStartQuantities.get(itemId);
			int currentQuantity = pendingHistoryCurrentQuantities.get(itemId);
			long startTimestamp = pendingHistoryStartTimestamps.get(itemId);
			List<SupplyBalanceHistoryPoint> history = recordBankSessionHistory(
				itemId,
				startTimestamp,
				startQuantity,
				closeTimestamp,
				currentQuantity);

			if (updatePanel && panel != null)
			{
				long inflow = totalInflow.getOrDefault(itemId, 0L);
				long outflow = totalOutflow.getOrDefault(itemId, 0L);
				long net = inflow - outflow;
				String itemName = client.getItemDefinition(itemId).getName();

				panel.updateItem(
					itemId,
					itemName,
					currentQuantity,
					inflow,
					outflow,
					net,
					history);
			}
		}

		clearPendingHistory();
	}

	private List<SupplyBalanceHistoryPoint> recordBankSessionHistory(
		int itemId,
		long startTimestamp,
		int startQuantity,
		long closeTimestamp,
		int currentQuantity)
	{
		List<SupplyBalanceHistoryPoint> history =
			itemHistory.computeIfAbsent(itemId, ignored -> new ArrayList<>());

		if (history.isEmpty()
			|| history.get(history.size() - 1).getQuantity() != startQuantity)
		{
			history.add(new SupplyBalanceHistoryPoint(startTimestamp, startQuantity));
		}

		long finalTimestamp = closeTimestamp;
		if (!history.isEmpty())
		{
			finalTimestamp = Math.max(
				finalTimestamp,
				history.get(history.size() - 1).getTimestamp() + 1);
		}
		history.add(new SupplyBalanceHistoryPoint(finalTimestamp, currentQuantity));

		trimAndSaveHistory(itemId, history);
		return new ArrayList<>(history);
	}

	private List<SupplyBalanceHistoryPoint> historySnapshot(int itemId)
	{
		return new ArrayList<>(itemHistory.getOrDefault(itemId, new ArrayList<>()));
	}

	private void clearPendingHistory()
	{
		pendingHistoryStartQuantities.clear();
		pendingHistoryCurrentQuantities.clear();
		pendingHistoryStartTimestamps.clear();
	}

	private void trimAndSaveHistory(
		int itemId,
		List<SupplyBalanceHistoryPoint> history)
	{
		while (history.size() > MAX_HISTORY_POINTS)
		{
			history.remove(0);
		}

		configManager.setRSProfileConfiguration(
			CONFIG_GROUP,
			HISTORY_KEY_PREFIX + itemId,
			encodeHistory(history));
	}

	private String encodeHistory(List<SupplyBalanceHistoryPoint> history)
	{
		StringBuilder encoded = new StringBuilder();
		for (SupplyBalanceHistoryPoint point : history)
		{
			if (encoded.length() > 0)
			{
				encoded.append(';');
			}

			encoded.append(point.getTimestamp())
				.append(',')
				.append(point.getQuantity());
		}
		return encoded.toString();
	}

	private List<SupplyBalanceHistoryPoint> decodeHistory(String encoded)
	{
		List<SupplyBalanceHistoryPoint> history = new ArrayList<>();
		if (encoded == null || encoded.isEmpty())
		{
			return history;
		}

		for (String encodedPoint : encoded.split(";"))
		{
			String[] parts = encodedPoint.split(",", -1);
			if (parts.length != 2)
			{
				continue;
			}

			try
			{
				long timestamp = Long.parseLong(parts[0]);
				int quantity = Integer.parseInt(parts[1]);
				if (timestamp > 0 && quantity >= 0)
				{
					history.add(new SupplyBalanceHistoryPoint(timestamp, quantity));
				}
			}
			catch (NumberFormatException ignored)
			{
				log.debug("Ignoring invalid saved history point: {}", encodedPoint);
			}
		}

		history.sort((first, second) ->
			Long.compare(first.getTimestamp(), second.getTimestamp()));
		while (history.size() > MAX_HISTORY_POINTS)
		{
			history.remove(0);
		}
		return history;
	}

	private void loadSavedHistories(String profileKey)
	{
		for (String key : configManager.getRSProfileConfigurationKeys(
			CONFIG_GROUP,
			profileKey,
			HISTORY_KEY_PREFIX))
		{
			try
			{
				String itemIdText = key.substring(HISTORY_KEY_PREFIX.length());
				int itemId = Integer.parseInt(itemIdText);
				if (!SupplyRegistry.isTracked(itemId))
				{
					continue;
				}

				String encoded = configManager.getRSProfileConfiguration(
					CONFIG_GROUP,
					key,
					String.class);
				List<SupplyBalanceHistoryPoint> history = decodeHistory(encoded);
				if (!history.isEmpty())
				{
					itemHistory.put(itemId, history);
				}
			}
			catch (NumberFormatException ignored)
			{
				log.debug("Ignoring invalid saved history key: {}", key);
			}
		}
	}

	private long loadSavedTotal(String keyPrefix, int itemId)
	{
		Long savedTotal = configManager.getRSProfileConfiguration(
				CONFIG_GROUP,
				keyPrefix + itemId,
				Long.class);

		if (savedTotal == null)
		{
			return 0L;
		}

		return savedTotal;
	}

	private void loadAllSavedTotals()
	{
		totalInflow.clear();
		totalOutflow.clear();
		itemHistory.clear();

		String profileKey = configManager.getRSProfileKey();

		if (profileKey == null)
		{
			log.debug("No active RuneScape profile");
			return;
		}

		loadSavedTotals(profileKey, INFLOW_KEY_PREFIX, totalInflow);
		loadSavedTotals(profileKey, OUTFLOW_KEY_PREFIX, totalOutflow);
		loadSavedHistories(profileKey);

		log.debug("Loaded {} inflow totals, {} outflow totals, and {} histories",
			totalInflow.size(), totalOutflow.size(), itemHistory.size());
	}

	private void loadSavedTotals(
			String profileKey,
			String keyPrefix,
			Map<Integer, Long> destination)
	{
		for (String key : configManager.getRSProfileConfigurationKeys(
				CONFIG_GROUP,
				profileKey,
				keyPrefix))
		{
			try
			{
				String itemIdText = key.substring(keyPrefix.length());
				int itemId = Integer.parseInt(itemIdText);
				if (!SupplyRegistry.isTracked(itemId))
				{
					continue;
				}

				long total = loadSavedTotal(keyPrefix, itemId);

				destination.put(itemId, total);
			}
			catch (NumberFormatException ignored)
			{
				log.debug("Ignoring invalid saved total key: {}", key);
			}
		}
	}
}
