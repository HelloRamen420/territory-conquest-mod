package com.example.territory.util;

import com.example.territory.TerritorySavedData;

public class TradeManager {
    public enum TradeItem {
        IRON, GOLD, DIAMOND, EMERALD, BOOK, HAY_BLOCK, NETHERITE_INGOT, NETHER_STAR
    }

    public enum PurchaseItem {
        TERRITORY_FLAG
    }

    public static class TradeRate {
        public final int requiredItems;
        public final int goldReward;

        public TradeRate(int requiredItems, int goldReward) {
            this.requiredItems = requiredItems;
            this.goldReward = goldReward;
        }
    }

    public static TradeRate getCurrentRate(TradeItem item, TerritorySavedData.PlayerState state) {
        switch (item) {
            case IRON:
                int ironStacks = state.totalIronSold / 64;
                if (ironStacks <= 5) return new TradeRate(1, 50);
                if (ironStacks <= 30) return new TradeRate(1, 10);
                return new TradeRate(16, 5);

            case GOLD:
                int goldStacks = state.totalGoldSold / 64;
                if (goldStacks <= 5) return new TradeRate(1, 150);
                if (goldStacks <= 25) return new TradeRate(1, 30);
                return new TradeRate(4, 5);

            case DIAMOND:
                return new TradeRate(1, 1500);

            case EMERALD:
                int emeraldStacks = state.totalEmeraldSold / 64;
                if (emeraldStacks <= 10) return new TradeRate(1, 300);
                return new TradeRate(1, 50);

            case BOOK:
                return new TradeRate(1, 10);

            case HAY_BLOCK:
                return new TradeRate(1, 5);

            case NETHERITE_INGOT:
                return new TradeRate(1, 15000);

            case NETHER_STAR:
                return new TradeRate(1, 100000);

            default:
                return new TradeRate(1, 1);
        }
    }

    public static int getPurchasePrice(PurchaseItem item) {
        switch (item) {
            case TERRITORY_FLAG:
                return 200; // Flag costs 200G
            default:
                return 999999;
        }
    }
}
