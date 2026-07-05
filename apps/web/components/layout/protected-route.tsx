"use client";

import { useAuthStore } from "@/features/auth/store";
import { getAuthTokens } from "@/features/auth/token-store";
import { ButtonLink } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { heliumApi } from "@/lib/api/client";
import { isApiError } from "@/lib/api/errors";
import { queryKeys } from "@/lib/query/keys";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";

export function ProtectedRoute({
  children,
  roles
}: Readonly<{
  children: React.ReactNode;
  roles?: string[];
}>) {
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  const endSession = useAuthStore((state) => state.endSession);
  const hasRole = useAuthStore((state) => state.hasRole);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => setHydrated(true), []);

  const hasTokens = hydrated && Boolean(getAuthTokens());

  // Validate/restore the session against the backend whenever tokens exist.
  const sessionQuery = useQuery({
    queryKey: queryKeys.session,
    queryFn: heliumApi.session,
    enabled: hasTokens,
    staleTime: 60_000,
    retry: (failureCount, error) => !(isApiError(error) && (error.isUnauthorized || error.isForbidden)) && failureCount < 2
  });

  useEffect(() => {
    if (sessionQuery.data) {
      setUser(sessionQuery.data);
    }
  }, [sessionQuery.data, setUser]);

  useEffect(() => {
    if (sessionQuery.error && isApiError(sessionQuery.error) && sessionQuery.error.isUnauthorized) {
      endSession();
    }
  }, [sessionQuery.error, endSession]);

  if (!hydrated) {
    return <main className="grid min-h-screen place-items-center p-6 text-foreground">Loading</main>;
  }

  if (!hasTokens || (!user && sessionQuery.isError)) {
    return (
      <main className="flex min-h-screen items-center justify-center px-6 text-foreground">
        <Card className="w-full max-w-md">
          <CardContent className="p-6">
            <p className="text-micro font-semibold uppercase text-cyan-200/80">Secure workspace</p>
            <h1 className="mt-2 text-title-lg">Sign in required</h1>
            <p className="mt-2 text-sm text-muted-foreground">Exchange access requires an active authenticated session.</p>
            <ButtonLink className="mt-5" href="/login">Go to login</ButtonLink>
          </CardContent>
        </Card>
      </main>
    );
  }

  if (!user && sessionQuery.isLoading) {
    return <main className="grid min-h-screen place-items-center p-6 text-foreground">Restoring session…</main>;
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
