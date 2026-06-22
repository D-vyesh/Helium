"use client";

import { Button } from "./button";
import type { ReactNode } from "react";

export function Dialog({
  open,
  title,
  children,
  onClose
}: Readonly<{ open: boolean; title: string; children: ReactNode; onClose: () => void }>) {
  if (!open) return null;
  return (
    <div aria-modal="true" className="fixed inset-0 z-50 grid place-items-center bg-black/70 p-4 backdrop-blur-sm" role="dialog">
      <div className="glass-panel w-full max-w-lg rounded-lg p-4 shadow-glow">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-title-lg">{title}</h2>
          <Button aria-label="Close dialog" onClick={onClose} size="sm" type="button" variant="ghost">X</Button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Modal(props: Readonly<{ open: boolean; title: string; children: ReactNode; onClose: () => void }>) {
  return <Dialog {...props} />;
}
