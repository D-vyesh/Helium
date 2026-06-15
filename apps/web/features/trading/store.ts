"use client";

import { create } from "zustand";

type TradingState = {
  selectedMarket: string;
  setSelectedMarket: (market: string) => void;
};

export const useTradingStore = create<TradingState>((set) => ({
  selectedMarket: "BTC-USD",
  setSelectedMarket: (market) => set({ selectedMarket: market })
}));
