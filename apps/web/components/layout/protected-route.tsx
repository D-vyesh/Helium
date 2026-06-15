"use client";

import { useAuthStore } from "@/features/auth/store";
import type { UserRole } from "@/lib/api/types";
import Link from "next/link";
import { useEffect, useState } from "react";

export function ProtectedRoute({
  children,
  roles
}: Readonly<{
  children: React.ReactNode;
  roles?: UserRole[];
}>) {
  const user = useAuthStore((state) => state.user);
  const hasRole = useAuthStore((state) => state.hasRole);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => setHydrated(true), []);

  if (!hydrated) {
    return <main className="min-h-screen bg-slate-950 p-6 text-slate-100">Loading</main>;
  }

  if (!user) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-950 px-6 text-slate-100">
        <section className="w-full max-w-md rounded border border-slate-800 bg-slate-900 p-6">
          <h1 className="text-xl font-semibold">Sign in required</h1>
          <p className="mt-2 text-sm text-slate-400">Closed-beta exchange access requires an active session.</p>
          <Link className="mt-5 inline-flex rounded bg-cyan-400 px-4 py-2 text-sm font-semibold text-slate-950" href="/login">
            Go to login
          </Link>
        </section>
      </main>
    );
  }

  if (roles && !hasRole(roles)) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-950 px-6 text-slate-100">
        <section className="w-full max-w-md rounded border border-slate-800 bg-slate-900 p-6">
          <h1 className="text-xl font-semibold">Access unavailable</h1>
          <p className="mt-2 text-sm text-slate-400">Your current role cannot open this workspace.</p>
        </section>
      </main>
    );
  }

  return <>{children}</>;
}
