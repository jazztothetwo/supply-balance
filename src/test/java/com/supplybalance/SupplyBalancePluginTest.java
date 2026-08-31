package com.supplybalance;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SupplyBalancePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SupplyBalancePlugin.class);
		RuneLite.main(args);
	}
}