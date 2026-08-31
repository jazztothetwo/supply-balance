package com.supplybalance;

import java.util.Set;
import net.runelite.api.gameval.ItemID;

final class SupplyRegistry
{
    private static final Set<Integer> TRACKED_ITEMS = Set.of(
            ItemID.SHARK,
            ItemID.MANTARAY,
            ItemID.ANGLERFISH
    );

    private SupplyRegistry()
    {
    }

    static boolean isTracked(int itemId)
    {
        return TRACKED_ITEMS.contains(itemId);
    }
}