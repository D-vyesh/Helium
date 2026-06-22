"use client";

import { cn } from "@/lib/utils/cn";
import { useState, type ReactNode } from "react";

export function Dropdown({ label, children }: Readonly<{ label: ReactNode; children: ReactNode }>) {
  const [open, setOpen] = useState(false);
  return (
    <div className="relative">
      <button
        aria-expanded={open}
        className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white/6 px-3 text-sm font-semibold text-foreground"
        onClick={() => setOpen((value) => !value)}
        type="button"
      >
        {label}
      </button>
      <div className={cn("absolute right-0 z-40 mt-2 min-w-48 rounded-md border border-border bg-slate-950 p-1 shadow-glow", !open && "hidden")}>
        {children}
      </div>
    </div>
  );
}

export function DropdownItem({ className, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button className={cn("block w-full rounded-sm px-3 py-2 text-left text-sm text-muted-foreground hover:bg-white/8 hover:text-foreground", className)} type="button" {...props} />;
}
