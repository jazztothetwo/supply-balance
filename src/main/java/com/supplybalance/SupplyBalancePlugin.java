package com.supplybalance;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.ItemComposition;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.config.ConfigManager;

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

	private Map<Integer, Integer> previousBankSnapshot;
	private final Map<Integer, Long> totalInflow = new HashMap<>();
	private final Map<Integer, Long> totalOutflow = new HashMap<>();

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Override
	protected void startUp()
	{
		previousBankSnapshot = null;
		totalInflow.clear();
		totalOutflow.clear();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		previousBankSnapshot = null;
		totalInflow.clear();
		totalOutflow.clear();
	}

	@Override
	protected void shutDown()
	{
		previousBankSnapshot = null;
		log.debug("Supply Balance stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState gameState = gameStateChanged.getGameState();

		if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING)
		{
			previousBankSnapshot = null;
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

            log.debug("Item {} changed by {} | In: {} Out: {} Net: {}",
                    itemId, change, inflow, outflow, net);
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
			if (itemID < 0 || quantity <= 0)
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
}
