"use client";

import { Button } from "./button";
import type { ReactNode } from "react";

export function Drawer({
  open,
  title,
  children,
  onClose
}: Readonly<{ open: boolean; title: string; children: ReactNode; onClose: () => void }>) {
  if (!open) return null;
  return (
    <div aria-modal="true" className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm" role="dialog">
      <aside className="glass-panel ml-auto h-full w-full max-w-md animate-fade-up overflow-y-auto rounded-l-lg p-4">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-title-lg">{title}</h2>
          <Button aria-label="Close drawer" onClick={onClose} size="sm" type="button" variant="ghost">X</Button>
        </div>
        {children}
      </aside>
    </div>
  );
}
