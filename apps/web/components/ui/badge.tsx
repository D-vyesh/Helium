import { cn } from "@/lib/utils/cn";
import type { HTMLAttributes } from "react";

const toneClass = {
  neutral: "border-border bg-white/6 text-muted-foreground",
  success: "border-emerald-400/25 bg-emerald-400/10 text-emerald-300",
  danger: "border-red-400/25 bg-red-400/10 text-red-300",
  warning: "border-amber-400/25 bg-amber-400/10 text-amber-200",
  info: "border-cyan-400/25 bg-cyan-400/10 text-cyan-200"
};

export function Badge({
  className,
  tone = "neutral",
  ...props
}: HTMLAttributes<HTMLSpanElement> & { tone?: keyof typeof toneClass }) {
  return (
    <span
      className={cn(
        "inline-flex h-6 items-center rounded-sm border px-2 text-micro font-semibold uppercase",
        toneClass[tone],
        className
      )}
      {...props}
    />
  );
}
