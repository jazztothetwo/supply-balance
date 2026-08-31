package com.supplybalance;

final class SupplyBalanceHistoryPoint
{
	private final long timestamp;
	private final int quantity;

	SupplyBalanceHistoryPoint(long timestamp, int quantity)
	{
		this.timestamp = timestamp;
		this.quantity = quantity;
	}

	long getTimestamp()
	{
		return timestamp;
	}

	int getQuantity()
	{
		return quantity;
	}
}
