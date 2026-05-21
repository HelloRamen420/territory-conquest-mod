package com.example.territory.client;

import com.example.territory.TerritorySavedData;
import java.util.ArrayList;
import java.util.List;

public class ClientAuctionData {
    private static List<TerritorySavedData.AuctionEntry> auctionList = new ArrayList<>();

    public static synchronized void setAuctionList(List<TerritorySavedData.AuctionEntry> list) {
        auctionList = new ArrayList<>(list);
    }

    public static synchronized List<TerritorySavedData.AuctionEntry> getAuctionList() {
        return new ArrayList<>(auctionList);
    }
}
