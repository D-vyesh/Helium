"use client";

import { useAuthStore } from "@/features/auth/store";
import { ButtonLink } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { UserRole } from "@/lib/api/types";
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
    return <main className="grid min-h-screen place-items-center p-6 text-foreground">Loading</main>;
  }

  if (!user) {
    return (
      <main className="flex min-h-screen items-center justify-center px-6 text-foreground">
        <Card className="w-full max-w-md">
          <CardContent className="p-6">
            <p className="text-micro font-semibold uppercase text-cyan-200/80">Secure workspace</p>
            <h1 className="mt-2 text-title-lg">Sign in required</h1>
            <p className="mt-2 text-sm text-muted-foreground">Closed-beta exchange access requires an active verified session.</p>
            <ButtonLink className="mt-5" href="/login">Go to login</ButtonLink>
          </CardContent>
        </Card>
      </main>
    );
  }

  if (roles && !hasRole(roles)) {
    return (
      <main className="flex min-h-screen items-center justify-center px-6 text-foreground">
        <Card className="w-full max-w-md">
          <CardContent className="p-6">
            <h1 className="text-title-lg">Access unavailable</h1>
            <p className="mt-2 text-sm text-muted-foreground">Your current role cannot open this workspace.</p>
          </CardContent>
        </Card>
      </main>
    );
  }

  return <>{children}</>;
}
