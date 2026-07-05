"use client";

import { ErrorState, LoadingState, NotImplemented } from "@/components/ui/state";
import { heliumApi } from "@/lib/api/client";
import { queryKeys } from "@/lib/query/keys";
import { useQuery } from "@tanstack/react-query";

export function SettingsPanel() {
  const query = useQuery({ queryKey: queryKeys.session, queryFn: heliumApi.session });
  if (query.isLoading) return <LoadingState label="Loading account" />;
  if (query.isError) return <ErrorState title="Could not load account" error={query.error} onRetry={() => void query.refetch()} />;
  const profile = query.data;
  if (!profile) return null;
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <section className="rounded border border-slate-800 bg-slate-900 p-4">
        <h2 className="text-lg font-semibold">Profile</h2>
        <dl className="mt-4 space-y-3 text-sm">
          <Row label="Email" value={profile.email} />
          <Row label="Display name" value={profile.displayName} />
          <Row label="Account status" value={profile.status} />
          <Row label="Email verified" value={profile.emailVerified ? "Yes" : "No"} />
          <Row label="Roles" value={profile.roles.length ? profile.roles.join(", ") : "USER"} />
          <Row label="Member since" value={new Date(profile.createdAt).toLocaleDateString()} />
        </dl>
      </section>
      <section className="space-y-4">
        <NotImplemented feature="Profile editing (display name, password change from this screen)" />
        <NotImplemented feature="Two-factor authentication management" />
      </section>
    </div>
  );
}

function Row({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-slate-800 pb-3">
      <dt className="text-slate-400">{label}</dt>
      <dd className="font-medium text-slate-100">{value}</dd>
    </div>
  );
}
