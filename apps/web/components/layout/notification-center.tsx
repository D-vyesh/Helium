"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { heliumApi } from "@/lib/api/client";
import type { ExchangeNotification } from "@/lib/api/types";
import { queryKeys } from "@/lib/query/keys";
import { cn } from "@/lib/utils/cn";
import { shortDate } from "@/lib/utils/format";
import { useNotificationStream } from "@/lib/ws/notification-stream";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";

const PAGE_SIZE = 50;

export function NotificationCenter() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const unreadQuery = useQuery({ queryKey: queryKeys.notificationUnreadCount, queryFn: heliumApi.notificationUnreadCount });
  const notificationsQuery = useInfiniteQuery({
    queryKey: queryKeys.notifications,
    queryFn: ({ pageParam }) => heliumApi.notifications(pageParam, PAGE_SIZE),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.length === PAGE_SIZE ? lastPage[lastPage.length - 1]?.createdAt : undefined
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.notifications });
    void queryClient.invalidateQueries({ queryKey: queryKeys.notificationUnreadCount });
  };

  const status = useNotificationStream((event) => {
    if (event.type === "notification" || event.type === "unread-count") {
      refresh();
    }
    if (event.type === "preferences") {
      void queryClient.invalidateQueries({ queryKey: queryKeys.preferences });
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboardActivity });
    }
  });

  const markRead = useMutation({
    mutationFn: heliumApi.markNotificationRead,
    onSuccess: refresh
  });
  const markAllRead = useMutation({
    mutationFn: heliumApi.markAllNotificationsRead,
    onSuccess: refresh
  });
  const remove = useMutation({
    mutationFn: heliumApi.deleteNotification,
    onSuccess: refresh
  });

  const notifications = useMemo(() => notificationsQuery.data?.pages.flat() ?? [], [notificationsQuery.data]);
  const grouped = useMemo(() => groupByDay(notifications), [notifications]);
  const unread = unreadQuery.data?.unread ?? 0;

  return (
    <div className="relative">
      <button
        aria-label="Notifications"
        className="relative inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white/6 px-3 text-sm font-semibold text-foreground transition hover:bg-white/10"
        onClick={() => setOpen((value) => !value)}
        type="button"
      >
        <BellIcon />
        <span className="hidden md:inline">Alerts</span>
        {unread > 0 ? (
          <span className="absolute -right-1 -top-1 grid min-w-5 place-items-center rounded-full bg-red-400 px-1.5 text-[10px] font-bold text-white">
            {unread > 99 ? "99+" : unread}
          </span>
        ) : null}
      </button>
      {open ? (
        <div className="absolute right-0 z-50 mt-2 w-[min(440px,calc(100vw-2rem))] overflow-hidden rounded-lg border border-border bg-slate-950 shadow-glow">
          <div className="flex items-center justify-between gap-3 border-b border-border/70 px-4 py-3">
            <div>
              <p className="text-sm font-semibold text-foreground">Notifications</p>
              <p className="text-xs text-muted-foreground">{status === "connected" ? "Live delivery connected" : "REST history active"}</p>
            </div>
            <div className="flex items-center gap-2">
              <Badge tone={status === "connected" ? "success" : "warning"}>{status === "connected" ? "Live" : "Sync"}</Badge>
              <Button disabled={!unread || markAllRead.isPending} onClick={() => markAllRead.mutate()} size="sm" type="button" variant="secondary">
                Read all
              </Button>
            </div>
          </div>
          <div className="max-h-[70vh] overflow-auto p-3">
            {notificationsQuery.isLoading ? <LoadingState label="Loading notifications" /> : null}
            {notificationsQuery.isError ? (
              <ErrorState title="Could not load notifications" error={notificationsQuery.error} onRetry={() => void notificationsQuery.refetch()} />
            ) : null}
            {!notificationsQuery.isLoading && !notificationsQuery.isError && !notifications.length ? (
              <EmptyState title="No notifications yet" detail="Trading, account, security, and system events will appear here as they happen." />
            ) : null}
            <div className="space-y-4">
              {grouped.map((group) => (
                <section className="space-y-2" key={group.day}>
                  <p className="px-1 text-micro font-semibold uppercase text-muted-foreground">{group.day}</p>
                  {group.items.map((notification) => (
                    <NotificationRow
                      key={notification.id}
                      notification={notification}
                      onDelete={() => remove.mutate(notification.id)}
                      onRead={() => markRead.mutate(notification.id)}
                    />
                  ))}
                </section>
              ))}
            </div>
            {notificationsQuery.hasNextPage ? (
              <Button
                className="mt-3 w-full"
                disabled={notificationsQuery.isFetchingNextPage}
                onClick={() => void notificationsQuery.fetchNextPage()}
                type="button"
                variant="secondary"
              >
                {notificationsQuery.isFetchingNextPage ? "Loading" : "Load more"}
              </Button>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function NotificationRow({
  notification,
  onRead,
  onDelete
}: Readonly<{ notification: ExchangeNotification; onRead: () => void; onDelete: () => void }>) {
  return (
    <article className={cn("rounded-md border border-border/70 bg-white/[0.035] p-3 text-sm", !notification.read && "border-cyan-300/30 bg-cyan-300/8")}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={categoryTone(notification.category)}>{notification.category}</Badge>
            {!notification.read ? <Badge tone="info">Unread</Badge> : null}
          </div>
          <p className="mt-2 font-semibold text-foreground">{notification.title}</p>
          <p className="mt-1 text-xs text-muted-foreground">{notification.message}</p>
          <p className="mt-2 text-micro uppercase text-muted-foreground">{shortDate(notification.createdAt)}</p>
        </div>
        <div className="flex shrink-0 flex-col gap-2">
          {!notification.read ? (
            <Button onClick={onRead} size="sm" type="button" variant="secondary">Read</Button>
          ) : null}
          <Button onClick={onDelete} size="sm" type="button" variant="ghost">Delete</Button>
        </div>
      </div>
    </article>
  );
}

function groupByDay(notifications: ExchangeNotification[]) {
  const map = new Map<string, ExchangeNotification[]>();
  notifications.forEach((notification) => {
    const day = new Intl.DateTimeFormat("en-US", { month: "short", day: "2-digit", year: "numeric" }).format(new Date(notification.createdAt));
    map.set(day, [...(map.get(day) ?? []), notification]);
  });
  return [...map.entries()].map(([day, items]) => ({ day, items }));
}

function categoryTone(category: string): "neutral" | "success" | "danger" | "warning" | "info" {
  if (category === "TRADING" || category === "MARKET") return "info";
  if (category === "SECURITY" || category === "ADMIN") return "warning";
  if (category === "WALLET") return "success";
  if (category === "SYSTEM") return "danger";
  return "neutral";
}

function BellIcon() {
  return (
    <svg aria-hidden className="h-4 w-4" fill="none" viewBox="0 0 24 24">
      <path d="M15 17h5l-1.4-1.4A2 2 0 0 1 18 14.2V11a6 6 0 1 0-12 0v3.2a2 2 0 0 1-.6 1.4L4 17h5" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.8" />
      <path d="M9 17a3 3 0 0 0 6 0" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
    </svg>
  );
}
