"use client";

import { useEffect, useRef, useState } from "react";
import { wsBaseUrl } from "@/lib/api/http";
import { getAuthTokens } from "@/features/auth/token-store";

export type TradingStreamStatus = "connecting" | "connected" | "disconnected" | "unavailable";

export type TradingSocketEvent = {
  type: string;
  time: string;
  payload: unknown;
};

const MAX_BACKOFF_MS = 15000;
const STALE_AFTER_MS = 45000;

export function useTradingStream(onEvent?: (event: TradingSocketEvent) => void): TradingStreamStatus {
  const [status, setStatus] = useState<TradingStreamStatus>("connecting");
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    const token = getAuthTokens()?.accessToken;
    if (!token) {
      setStatus("unavailable");
      return;
    }
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
        socket = new WebSocket(`${base}/ws/trading/orders?token=${encodeURIComponent(token)}`);
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
          const event = JSON.parse(message.data as string) as TradingSocketEvent;
          lastFrameAt = Date.now();
          onEventRef.current?.(event);
        } catch {
          // REST polling remains the fallback source of truth.
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
      reconnectTimer = setTimeout(connect, Math.min(1000 * 2 ** Math.min(attempts, 4), MAX_BACKOFF_MS));
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
  }, []);

  return status;
}
