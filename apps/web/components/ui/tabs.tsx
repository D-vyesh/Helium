"use client";

import { cn } from "@/lib/utils/cn";
import { useState, type ReactNode } from "react";

export function Tabs({ tabs, defaultValue }: Readonly<{ tabs: { value: string; label: string; content: ReactNode }[]; defaultValue?: string }>) {
  const [active, setActive] = useState(defaultValue ?? tabs[0]?.value);
  return (
    <div>
      <div aria-label="Tabs" className="inline-flex rounded-md border border-border bg-black/20 p-1" role="tablist">
        {tabs.map((tab) => (
          <button
            aria-controls={`${tab.value}-panel`}
            aria-selected={active === tab.value}
            className={cn("h-8 rounded-sm px-3 text-xs font-semibold text-muted-foreground transition", active === tab.value && "bg-white/10 text-foreground")}
            id={`${tab.value}-tab`}
            key={tab.value}
            onClick={() => setActive(tab.value)}
            role="tab"
            type="button"
          >
            {tab.label}
          </button>
        ))}
      </div>
      {tabs.map((tab) => (
        <div aria-labelledby={`${tab.value}-tab`} hidden={active !== tab.value} id={`${tab.value}-panel`} key={tab.value} role="tabpanel">
          {tab.content}
        </div>
      ))}
    </div>
  );
}
