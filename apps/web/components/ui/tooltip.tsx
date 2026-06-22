import type { ReactNode } from "react";

export function Tooltip({ label, children }: Readonly<{ label: string; children: ReactNode }>) {
  return (
    <span className="group relative inline-flex">
      {children}
      <span
        className="pointer-events-none absolute bottom-full left-1/2 z-30 mb-2 hidden -translate-x-1/2 whitespace-nowrap rounded-sm border border-border bg-slate-950 px-2 py-1 text-xs text-foreground shadow-glow group-hover:block group-focus-within:block"
        role="tooltip"
      >
        {label}
      </span>
    </span>
  );
}
