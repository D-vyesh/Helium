"use client";

import { create } from "zustand";

type TradingState = {
  selectedMarket: string;
  setSelectedMarket: (market: string) => void;
};

export const useTradingStore = create<TradingState>((set) => ({
  selectedMarket: "BTCUSDT",
  setSelectedMarket: (market) => set({ selectedMarket: market })
}));
