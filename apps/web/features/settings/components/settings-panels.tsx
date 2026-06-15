"use client";

import { ErrorState, LoadingState } from "@/components/ui/state";
import { heliumApi } from "@/lib/api/client";
import { queryKeys } from "@/lib/query/keys";
import { useQuery } from "@tanstack/react-query";

export function SettingsPanel() {
  const query = useQuery({ queryKey: queryKeys.settings, queryFn: heliumApi.settings });
  if (query.isLoading) return <LoadingState label="Loading settings" />;
  if (query.isError) return <ErrorState title="Could not load settings" />;
  const profile = query.data;
  if (!profile) return null;
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <section className="rounded border border-slate-800 bg-slate-900 p-4">
        <h2 className="text-lg font-semibold">Profile</h2>
        <dl className="mt-4 space-y-3 text-sm">
          <Row label="Email" value={profile.email} />
          <Row label="Display name" value={profile.displayName} />
          <Row label="Account status" value={profile.accountStatus} />
        </dl>
      </section>
      <section className="rounded border border-slate-800 bg-slate-900 p-4">
        <h2 className="text-lg font-semibold">Security</h2>
        <dl className="mt-4 space-y-3 text-sm">
          <Row label="MFA" value={profile.mfaEnabled ? "Enabled" : "Not enabled"} />
          <Row label="Sessions" value="Managed by Auth/User" />
          <Row label="Audit" value="Security events recorded server-side" />
        </dl>
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
