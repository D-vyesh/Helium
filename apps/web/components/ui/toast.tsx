"use client";

import { cn } from "@/lib/utils/cn";
import { useEffect, useState } from "react";

export function Toast({ message, tone = "info" }: Readonly<{ message?: string; tone?: "info" | "success" | "danger" }>) {
  const [visible, setVisible] = useState(Boolean(message));
  useEffect(() => {
    setVisible(Boolean(message));
    if (!message) return;
    const timer = window.setTimeout(() => setVisible(false), 4200);
    return () => window.clearTimeout(timer);
  }, [message]);
  if (!visible || !message) return null;
  return (
    <div
      className={cn(
        "fixed bottom-4 right-4 z-50 rounded-md border px-4 py-3 text-sm shadow-glow backdrop-blur-md",
        tone === "success" && "border-emerald-400/30 bg-emerald-950/80 text-emerald-100",
        tone === "danger" && "border-red-400/30 bg-red-950/80 text-red-100",
        tone === "info" && "border-cyan-400/30 bg-slate-950/80 text-cyan-100"
      )}
      role="status"
    >
      {message}
    </div>
  );
}
