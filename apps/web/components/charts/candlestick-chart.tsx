"use client";

import {
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp
} from "lightweight-charts";
import { useEffect, useRef } from "react";
import type { CandleResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils/cn";

// Canvas colors mirroring the design tokens in app/globals.css
// (lightweight-charts paints to canvas, so CSS variables are not usable here).
const UP = "#17cf7f"; // --success
const DOWN = "#ee4f59"; // --danger
const UP_FADED = "rgba(23, 207, 127, 0.35)";
const DOWN_FADED = "rgba(238, 79, 89, 0.35)";
const GRID = "rgba(36, 43, 56, 0.6)"; // --border
const TEXT = "#949fad"; // --muted-foreground

export function CandlestickChart({ candles, className }: Readonly<{ candles: CandleResponse[]; className?: string }>) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const priceSeriesRef = useRef<ISeriesApi<"Candlestick"> | null>(null);
  const volumeSeriesRef = useRef<ISeriesApi<"Histogram"> | null>(null);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }
    const chart = createChart(containerRef.current, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: TEXT
      },
      grid: {
        vertLines: { color: GRID },
        horzLines: { color: GRID }
      },
      timeScale: { timeVisible: true, secondsVisible: false, borderColor: GRID },
      rightPriceScale: { borderColor: GRID }
    });
    const priceSeries = chart.addSeries(CandlestickSeries, {
      upColor: UP,
      downColor: DOWN,
      wickUpColor: UP,
      wickDownColor: DOWN,
      borderVisible: false
    });
    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: "volume" },
      priceScaleId: ""
    });
    volumeSeries.priceScale().applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });

    chartRef.current = chart;
    priceSeriesRef.current = priceSeries;
    volumeSeriesRef.current = volumeSeries;
    return () => {
      chart.remove();
      chartRef.current = null;
      priceSeriesRef.current = null;
      volumeSeriesRef.current = null;
    };
  }, []);

  useEffect(() => {
    // lightweight-charts requires strictly ascending, unique timestamps.
    const rows = candles
      .map((candle) => ({ ...candle, time: Math.floor(Date.parse(candle.openTime) / 1000) as UTCTimestamp }))
      .sort((a, b) => a.time - b.time)
      .filter((row, index, all) => index === 0 || row.time !== all[index - 1].time);
    priceSeriesRef.current?.setData(
      rows.map(({ time, open, high, low, close }) => ({ time, open, high, low, close }))
    );
    volumeSeriesRef.current?.setData(
      rows.map(({ time, open, close, volume }) => ({
        time,
        value: volume,
        color: close >= open ? UP_FADED : DOWN_FADED
      }))
    );
    chartRef.current?.timeScale().fitContent();
  }, [candles]);

  return <div className={cn("h-72 w-full", className)} ref={containerRef} />;
}
