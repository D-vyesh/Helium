"use client";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ErrorState, LoadingState } from "@/components/ui/state";
import { ChangePasswordForm, TotpDisableForm, TotpSetupForm } from "@/features/auth/components/auth-forms";
import { useAuthStore } from "@/features/auth/store";
import { heliumApi } from "@/lib/api/client";
import type { PriceAlertCondition, UserPreferencesBody } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";
import { formatAmount } from "@/lib/utils/format";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";

export function SettingsPanel() {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.session, queryFn: heliumApi.session });
  const sessions = useQuery({ queryKey: queryKeys.authSessions, queryFn: heliumApi.sessions });
  const endSession = useAuthStore((state) => state.endSession);
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [showMfaSetup, setShowMfaSetup] = useState(false);
  const [showMfaDisable, setShowMfaDisable] = useState(false);

  const revokeSession = useMutation({
    mutationFn: heliumApi.revokeSession,
    onSuccess: async (_, sessionId) => {
      const revokedCurrent = sessions.data?.find((session) => session.id === sessionId)?.current;
      if (revokedCurrent) {
        endSession();
      }
      await queryClient.invalidateQueries({ queryKey: queryKeys.authSessions });
    }
  });

  const revokeAll = useMutation({
    mutationFn: heliumApi.revokeAllSessions,
    onSuccess: async () => {
      endSession();
      await queryClient.invalidateQueries({ queryKey: queryKeys.authSessions });
    }
  });

  if (query.isLoading) return <LoadingState label="Loading account" />;
  if (query.isError) return <ErrorState title="Could not load account" error={query.error} onRetry={() => void query.refetch()} />;
  const profile = query.data;
  if (!profile) return null;

  const hasMfa = profile.mfaEnabled;

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <section className="rounded border border-slate-800 bg-slate-900 p-4">
        <h2 className="text-lg font-semibold">Profile</h2>
        <dl className="mt-4 space-y-3 text-sm">
          <Row label="Email" value={profile.email} />
          <Row label="Display name" value={profile.displayName} />
          <Row label="Account status" value={profile.status} />
          <Row label="Email verified" value={profile.emailVerified ? "Yes" : "No"} />
          <Row label="2FA" value={hasMfa ? "Enabled" : "Disabled"} />
          <Row label="Roles" value={profile.roles.length ? profile.roles.join(", ") : "USER"} />
          <Row label="Member since" value={new Date(profile.createdAt).toLocaleDateString()} />
        </dl>
      </section>

      <PreferencesPanel />

      <section className="space-y-4" id="security">
        <div className="rounded border border-slate-800 bg-slate-900 p-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">Password</h2>
            <button
              className="text-sm text-cyan-200 hover:text-cyan-100"
              onClick={() => setShowChangePassword(!showChangePassword)}
            >
              {showChangePassword ? "Cancel" : "Change"}
            </button>
          </div>
          {showChangePassword ? (
            <div className="mt-4">
              <ChangePasswordForm
                onSuccess={() => {
                  setShowChangePassword(false);
                  void queryClient.invalidateQueries({ queryKey: queryKeys.session });
                }}
              />
            </div>
          ) : null}
        </div>

        <div className="rounded border border-slate-800 bg-slate-900 p-4">
          <div className="flex items-center justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold">Two-Factor Authentication</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {hasMfa ? "2FA is enabled on your account." : "Protect your account with an authenticator app."}
              </p>
            </div>
            {!showMfaSetup && !showMfaDisable ? (
              <button
                className={`text-sm ${hasMfa ? "text-red-400 hover:text-red-300" : "text-cyan-200 hover:text-cyan-100"}`}
                onClick={() => (hasMfa ? setShowMfaDisable(true) : setShowMfaSetup(true))}
              >
                {hasMfa ? "Disable" : "Enable"}
              </button>
            ) : null}
          </div>
          {showMfaSetup ? (
            <div className="mt-4">
              <TotpSetupForm
                onSuccess={() => {
                  setShowMfaSetup(false);
                  void queryClient.invalidateQueries({ queryKey: queryKeys.session });
                }}
              />
              <button className="mt-3 text-sm text-muted-foreground hover:text-foreground" onClick={() => setShowMfaSetup(false)}>Cancel</button>
            </div>
          ) : null}
          {showMfaDisable ? (
            <div className="mt-4">
              <TotpDisableForm
                onSuccess={() => {
                  setShowMfaDisable(false);
                  void queryClient.invalidateQueries({ queryKey: queryKeys.session });
                }}
              />
              <button className="mt-3 text-sm text-muted-foreground hover:text-foreground" onClick={() => setShowMfaDisable(false)}>Cancel</button>
            </div>
          ) : null}
        </div>
      </section>

      <section className="rounded border border-slate-800 bg-slate-900 p-4 lg:col-span-2" id="sessions">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-semibold">Active Sessions</h2>
            <p className="mt-1 text-sm text-muted-foreground">Review devices signed in to your account.</p>
          </div>
          <button
            className="rounded border border-red-500/40 px-3 py-2 text-sm font-medium text-red-300 hover:border-red-400 hover:text-red-200 disabled:opacity-50"
            disabled={revokeAll.isPending || !sessions.data?.length}
            onClick={() => revokeAll.mutate()}
          >
            {revokeAll.isPending ? "Revoking..." : "Logout all devices"}
          </button>
        </div>

        <div className="mt-4">
          {sessions.isLoading ? <LoadingState label="Loading sessions" /> : null}
          {sessions.isError ? <ErrorState title="Could not load sessions" error={sessions.error} onRetry={() => void sessions.refetch()} /> : null}
          {sessions.data?.length ? (
            <div className="overflow-hidden rounded border border-slate-800">
              {sessions.data.map((session) => (
                <div key={session.id} className="grid gap-3 border-b border-slate-800 p-4 last:border-b-0 md:grid-cols-[minmax(0,1fr)_180px_120px] md:items-center">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium text-slate-100">{session.deviceName}</p>
                      {session.current ? <span className="rounded bg-cyan-400/10 px-2 py-0.5 text-xs font-medium text-cyan-200">Current</span> : null}
                      <span className="rounded bg-slate-800 px-2 py-0.5 text-xs text-slate-300">{session.status}</span>
                    </div>
                    <p className="mt-1 break-all text-xs text-slate-400">{session.userAgent}</p>
                    <p className="mt-1 text-xs text-slate-500">IP {session.ipAddress}</p>
                  </div>
                  <div className="text-sm text-slate-300">
                    <p>Last active {formatDateTime(session.lastSeenAt)}</p>
                    <p className="mt-1 text-xs text-slate-500">Created {formatDateTime(session.createdAt)}</p>
                  </div>
                  <button
                    className="rounded border border-slate-700 px-3 py-2 text-sm text-slate-200 hover:border-red-500/60 hover:text-red-300 disabled:opacity-50"
                    disabled={revokeSession.isPending || session.status !== "ACTIVE"}
                    onClick={() => revokeSession.mutate(session.id)}
                  >
                    {session.current ? "Logout" : "Revoke"}
                  </button>
                </div>
              ))}
            </div>
          ) : null}
          {revokeSession.isError ? <p className="mt-3 text-sm text-red-300">Could not revoke session.</p> : null}
          {revokeAll.isError ? <p className="mt-3 text-sm text-red-300">Could not logout all devices.</p> : null}
        </div>
      </section>

      <PriceAlertsPanel />
    </div>
  );
}

const CHART_INTERVALS = ["1m", "5m", "15m", "30m", "1H", "4H", "1D", "1W", "1M"];

function PreferencesPanel() {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.preferences, queryFn: heliumApi.preferences });
  const [draft, setDraft] = useState<UserPreferencesBody | null>(null);
  const [lastSaved, setLastSaved] = useState("");
  const save = useMutation({
    mutationFn: heliumApi.updatePreferences,
    onSuccess: async (preferences) => {
      const next = bodyFromPreferences(preferences);
      setDraft(next);
      setLastSaved(signature(next));
      await queryClient.invalidateQueries({ queryKey: queryKeys.preferences });
      await queryClient.invalidateQueries({ queryKey: queryKeys.dashboardActivity });
    }
  });

  useEffect(() => {
    if (!query.data) return;
    const next = bodyFromPreferences(query.data);
    setDraft(next);
    setLastSaved(signature(next));
  }, [query.data]);

  useEffect(() => {
    if (!draft || !lastSaved) return;
    const current = signature(draft);
    if (current === lastSaved) return;
    const timer = window.setTimeout(() => save.mutate(draft), 800);
    return () => window.clearTimeout(timer);
  }, [draft, lastSaved, save]);

  const update = <K extends keyof UserPreferencesBody>(key: K, value: UserPreferencesBody[K]) => {
    setDraft((current) => current ? { ...current, [key]: value } : current);
  };
  const updateNotification = (key: string, value: boolean) => {
    setDraft((current) => current ? {
      ...current,
      notificationPreferences: { ...current.notificationPreferences, [key]: value }
    } : current);
  };
  const updateOrderDefault = (key: string, value: string) => {
    setDraft((current) => current ? {
      ...current,
      orderDefaults: { ...current.orderDefaults, [key]: value }
    } : current);
  };

  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4 lg:col-span-2" id="preferences">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold">Exchange Preferences</h2>
          <p className="mt-1 text-sm text-muted-foreground">Server-persisted defaults for trading, charts, notifications, and workspace behavior.</p>
        </div>
        <Badge tone={save.isPending ? "warning" : save.isError ? "danger" : "success"}>
          {save.isPending ? "Saving" : save.isError ? "Save failed" : "Synced"}
        </Badge>
      </div>
      {query.isLoading ? <div className="mt-4"><LoadingState label="Loading preferences" /></div> : null}
      {query.isError ? <div className="mt-4"><ErrorState title="Could not load preferences" error={query.error} onRetry={() => void query.refetch()} /></div> : null}
      {draft ? (
        <div className="mt-4 grid gap-4 lg:grid-cols-3">
          <div className="space-y-3 rounded-md border border-slate-800 bg-black/18 p-3">
            <h3 className="text-sm font-semibold text-slate-100">Display</h3>
            <Select label="Theme" value={draft.theme} onChange={(value) => update("theme", value)} options={["SYSTEM", "DARK", "LIGHT"]} />
            <TextInput label="Timezone" value={draft.timezone} onChange={(value) => update("timezone", value)} />
            <TextInput label="Language" value={draft.language} onChange={(value) => update("language", value)} />
            <TextInput label="Fiat display" value={draft.preferredFiat} onChange={(value) => update("preferredFiat", value.toUpperCase())} />
          </div>

          <div className="space-y-3 rounded-md border border-slate-800 bg-black/18 p-3">
            <h3 className="text-sm font-semibold text-slate-100">Trading Defaults</h3>
            <TextInput label="Default market" value={draft.defaultMarket} onChange={(value) => update("defaultMarket", value.toUpperCase())} />
            <Select label="Chart interval" value={draft.chartInterval} onChange={(value) => update("chartInterval", value)} options={CHART_INTERVALS} />
            <Select label="Chart style" value={draft.chartStyle} onChange={(value) => update("chartStyle", value)} options={["CANDLES", "BARS", "LINE"]} />
            <Select label="Order side" value={String(draft.orderDefaults.side ?? "BUY")} onChange={(value) => updateOrderDefault("side", value)} options={["BUY", "SELL"]} />
            <Select label="Order type" value={String(draft.orderDefaults.type ?? "LIMIT")} onChange={(value) => updateOrderDefault("type", value)} options={["LIMIT", "MARKET"]} />
          </div>

          <div className="space-y-3 rounded-md border border-slate-800 bg-black/18 p-3">
            <h3 className="text-sm font-semibold text-slate-100">Workspace & Notifications</h3>
            <Select label="Sidebar layout" value={draft.sidebarLayout} onChange={(value) => update("sidebarLayout", value)} options={["EXPANDED", "COMPACT", "COLLAPSED"]} />
            {["inApp", "email", "push", "orders", "trades", "security", "market"].map((key) => (
              <label className="flex items-center justify-between gap-3 rounded-sm border border-slate-800 px-3 py-2 text-sm" key={key}>
                <span className="capitalize text-slate-300">{key.replace(/([A-Z])/g, " $1")}</span>
                <input
                  checked={Boolean(draft.notificationPreferences[key])}
                  onChange={(event) => updateNotification(key, event.target.checked)}
                  type="checkbox"
                />
              </label>
            ))}
          </div>
        </div>
      ) : null}
      {query.data?.updatedAt ? <p className="mt-3 text-xs text-muted-foreground">Last synced {formatDateTime(query.data.updatedAt)}</p> : null}
      {save.isError ? <p className="mt-2 text-sm text-red-300">Preference update was rejected by the backend.</p> : null}
    </section>
  );
}

function bodyFromPreferences(preferences: { theme: string; timezone: string; language: string; preferredFiat: string; chartInterval: string; chartStyle: string; defaultMarket: string; sidebarLayout: string; workspaceLayout: Record<string, unknown>; orderDefaults: Record<string, unknown>; notificationPreferences: Record<string, unknown> }): UserPreferencesBody {
  return {
    theme: preferences.theme,
    timezone: preferences.timezone,
    language: preferences.language,
    preferredFiat: preferences.preferredFiat,
    chartInterval: preferences.chartInterval,
    chartStyle: preferences.chartStyle,
    defaultMarket: preferences.defaultMarket,
    sidebarLayout: preferences.sidebarLayout,
    workspaceLayout: preferences.workspaceLayout ?? {},
    orderDefaults: preferences.orderDefaults ?? {},
    notificationPreferences: preferences.notificationPreferences ?? {}
  };
}

function signature(value: UserPreferencesBody) {
  return JSON.stringify(value);
}

function TextInput({ label, value, onChange }: Readonly<{ label: string; value: string; onChange: (value: string) => void }>) {
  return (
    <label className="block text-sm text-slate-300">
      {label}
      <input
        className="mt-1 h-10 w-full rounded-md border border-slate-700 bg-black/20 px-3 text-sm text-slate-100"
        onChange={(event) => onChange(event.target.value)}
        value={value}
      />
    </label>
  );
}

function Select({ label, value, options, onChange }: Readonly<{ label: string; value: string; options: string[]; onChange: (value: string) => void }>) {
  return (
    <label className="block text-sm text-slate-300">
      {label}
      <select
        className="mt-1 h-10 w-full rounded-md border border-slate-700 bg-black/20 px-3 text-sm text-slate-100"
        onChange={(event) => onChange(event.target.value)}
        value={value}
      >
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </label>
  );
}

const ALERT_CONDITIONS: { value: PriceAlertCondition; label: string }[] = [
  { value: "PRICE_ABOVE", label: "Price >" },
  { value: "PRICE_BELOW", label: "Price <" },
  { value: "CROSSES_ABOVE", label: "Crosses above" },
  { value: "CROSSES_BELOW", label: "Crosses below" },
  { value: "CHANGE_PERCENT_ABOVE", label: "24h change exceeds %" },
  { value: "VOLUME_ABOVE", label: "Volume exceeds" }
];

function PriceAlertsPanel() {
  const queryClient = useQueryClient();
  const alerts = useQuery({ queryKey: queryKeys.priceAlerts, queryFn: heliumApi.priceAlerts });
  const [marketSymbol, setMarketSymbol] = useState("BTCUSDT");
  const [conditionType, setConditionType] = useState<PriceAlertCondition>("PRICE_ABOVE");
  const [threshold, setThreshold] = useState("");
  const [repeating, setRepeating] = useState(false);
  const create = useMutation({
    mutationFn: heliumApi.createPriceAlert,
    onSuccess: async () => {
      setThreshold("");
      await queryClient.invalidateQueries({ queryKey: queryKeys.priceAlerts });
    }
  });
  const enable = useMutation({
    mutationFn: heliumApi.enablePriceAlert,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: queryKeys.priceAlerts })
  });
  const disable = useMutation({
    mutationFn: heliumApi.disablePriceAlert,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: queryKeys.priceAlerts })
  });
  const remove = useMutation({
    mutationFn: heliumApi.deletePriceAlert,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: queryKeys.priceAlerts })
  });

  const submit = () => {
    create.mutate({
      marketSymbol,
      conditionType,
      threshold,
      repeating,
      enabled: true,
      deliveryInApp: true,
      deliveryEmail: false,
      deliveryPush: false
    });
  };

  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4 lg:col-span-2" id="price-alerts">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold">Price Alerts</h2>
          <p className="mt-1 text-sm text-muted-foreground">Alerts are evaluated server-side against live market data and delivered through notifications.</p>
        </div>
        <Badge tone="info">Server-side</Badge>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-[160px_220px_160px_120px_auto] md:items-end">
        <label className="text-sm">
          Market
          <input className="mt-1 h-10 w-full rounded-md border border-slate-700 bg-black/20 px-3 font-mono text-sm" value={marketSymbol} onChange={(event) => setMarketSymbol(event.target.value.toUpperCase())} />
        </label>
        <label className="text-sm">
          Condition
          <select className="mt-1 h-10 w-full rounded-md border border-slate-700 bg-black/20 px-3 text-sm" value={conditionType} onChange={(event) => setConditionType(event.target.value as PriceAlertCondition)}>
            {ALERT_CONDITIONS.map((condition) => <option key={condition.value} value={condition.value}>{condition.label}</option>)}
          </select>
        </label>
        <label className="text-sm">
          Threshold
          <input className="mt-1 h-10 w-full rounded-md border border-slate-700 bg-black/20 px-3 font-mono text-sm" inputMode="decimal" value={threshold} onChange={(event) => setThreshold(event.target.value)} />
        </label>
        <label className="flex h-10 items-center gap-2 text-sm">
          <input checked={repeating} onChange={(event) => setRepeating(event.target.checked)} type="checkbox" />
          Repeating
        </label>
        <Button disabled={create.isPending || !marketSymbol || !Number(threshold)} onClick={submit} type="button">Create alert</Button>
      </div>
      {create.isError ? <p className="mt-3 text-sm text-red-300">Could not create alert.</p> : null}
      <div className="mt-4">
        {alerts.isLoading ? <LoadingState label="Loading price alerts" /> : null}
        {alerts.isError ? <ErrorState title="Could not load price alerts" error={alerts.error} onRetry={() => void alerts.refetch()} /> : null}
        {alerts.data?.length ? (
          <div className="overflow-hidden rounded border border-slate-800">
            {alerts.data.map((alert) => (
              <div className="grid gap-3 border-b border-slate-800 p-4 last:border-b-0 lg:grid-cols-[1fr_180px_190px] lg:items-center" key={alert.id}>
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-semibold text-slate-100">{alert.marketSymbol}</p>
                    <Badge tone={alert.enabled ? "success" : "neutral"}>{alert.enabled ? "Enabled" : "Disabled"}</Badge>
                    {alert.repeating ? <Badge tone="info">Repeating</Badge> : null}
                  </div>
                  <p className="mt-1 text-sm text-slate-300">{humanAlert(alert.conditionType)} {formatAmount(alert.threshold)}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Last price {formatAmount(alert.lastEvaluatedPrice)} · Triggered {alert.triggeredAt ? formatDateTime(alert.triggeredAt) : "never"}
                  </p>
                </div>
                <div className="text-sm text-slate-300">
                  <p>Created {formatDateTime(alert.createdAt)}</p>
                  <p className="mt-1 text-xs text-slate-500">Delivery: in-app</p>
                </div>
                <div className="flex flex-wrap gap-2 lg:justify-end">
                  <Button
                    disabled={enable.isPending || disable.isPending}
                    onClick={() => (alert.enabled ? disable.mutate(alert.id) : enable.mutate(alert.id))}
                    size="sm"
                    type="button"
                    variant="secondary"
                  >
                    {alert.enabled ? "Disable" : "Enable"}
                  </Button>
                  <Button disabled={remove.isPending} onClick={() => remove.mutate(alert.id)} size="sm" type="button" variant="ghost">Delete</Button>
                </div>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </section>
  );
}

function humanAlert(value: string) {
  return ALERT_CONDITIONS.find((condition) => condition.value === value)?.label ?? value;
}

function Row({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-slate-800 pb-3">
      <dt className="text-slate-400">{label}</dt>
      <dd className="font-medium text-slate-100">{value}</dd>
    </div>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
