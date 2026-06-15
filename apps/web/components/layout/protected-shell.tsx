"use client";

import { AppShell } from "./app-shell";
import { ProtectedRoute } from "./protected-route";

export function ProtectedShell({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <ProtectedRoute>
      <AppShell>{children}</AppShell>
    </ProtectedRoute>
  );
}
