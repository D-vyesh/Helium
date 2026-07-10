"use client";

import { useEffect, useRef, useState } from "react";
import { wsBaseUrl } from "@/lib/api/http";

export type MarketChannel = "ticker" | "trades" | "orderbook" | "candles";
export type StreamStatus = "connecting" | "connected" | "disconnected" | "unavailable";

export type MarketSocketEvent = {
  type: string;
  topic: string;
  time: string;
  payload: unknown;
};

const MAX_BACKOFF_MS = 15000;
const STALE_AFTER_MS = 45000;

/**
 * Connects to the backend market-data WebSocket
 * (/ws/markets/{symbol}/{channel}) with automatic reconnection.
 *
 * The hook reports connection status and invokes `onEvent` for every
 * server-pushed event so callers can refresh their REST-backed queries.
 */
export function useMarketStream(
  symbol: string,
  channel: MarketChannel,
  onEvent?: (event: MarketSocketEvent) => void
): StreamStatus {
  const [status, setStatus] = useState<StreamStatus>("connecting");
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    let socket: WebSocket | null = null;
    let disposed = false;
    let attempts = 0;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let staleTimer: ReturnType<typeof setInterval> | null = null;
    let lastFrameAt = Date.now();

    let base: string;
    try {
      base = wsBaseUrl();
    } catch {
      setStatus("unavailable");
      return;
    }

    const connect = () => {
      if (disposed) return;
      setStatus(attempts === 0 ? "connecting" : "disconnected");
      try {
        socket = new WebSocket(`${base}/ws/markets/${encodeURIComponent(symbol)}/${channel}`);
      } catch {
        scheduleReconnect();
        return;
      }

      socket.onopen = () => {
        attempts = 0;
        lastFrameAt = Date.now();
        setStatus("connected");
      };
      socket.onmessage = (message) => {
        try {
          const event = JSON.parse(message.data as string) as MarketSocketEvent;
          lastFrameAt = Date.now();
          onEventRef.current?.(event);
        } catch {
          // Ignore malformed frames; REST polling remains the source of truth.
        }
      };
      socket.onclose = () => {
        if (!disposed) {
          setStatus("disconnected");
          scheduleReconnect();
        }
      };
      socket.onerror = () => {
        socket?.close();
      };
    };

    const scheduleReconnect = () => {
      if (disposed) return;
      attempts += 1;
      const delay = Math.min(1000 * 2 ** Math.min(attempts, 4), MAX_BACKOFF_MS);
      reconnectTimer = setTimeout(connect, delay);
    };

    connect();
    staleTimer = setInterval(() => {
      if (socket?.readyState === WebSocket.OPEN && Date.now() - lastFrameAt > STALE_AFTER_MS) {
        socket.close();
      }
    }, 5000);

    return () => {
      disposed = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (staleTimer) clearInterval(staleTimer);
      socket?.close();
    };
  }, [symbol, channel]);

  return status;
}
