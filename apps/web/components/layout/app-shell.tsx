"use client";

import { Button } from "@/components/ui/button";
import { Dropdown, DropdownItem } from "@/components/ui/dropdown";
import { useAuthStore } from "@/features/auth/store";
import { heliumApi } from "@/lib/api/client";
import { cn } from "@/lib/utils/cn";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

const navItems = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/markets", label: "Markets" },
  { href: "/trade", label: "Trade" },
  { href: "/wallet", label: "Wallet" },
  { href: "/orders/open", label: "Open Orders" },
  { href: "/orders/history", label: "Order History" },
  { href: "/trades/history", label: "Trade History" },
  { href: "/settings", label: "Settings" }
];

export function AppShell({ children }: Readonly<{ children: React.ReactNode }>) {
  const pathname = usePathname();
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  const canUseAdmin = Boolean(user?.roles.some((role) => role === "ADMIN" || role === "FINANCE_OPS" || role === "COMPLIANCE"));
  const logout = useMutation({
    mutationFn: heliumApi.logout,
    onSettled: () => {
      setUser(null);
      router.push("/login");
    }
  });

  return (
    <div className="min-h-screen text-foreground">
      <header className="sticky top-0 z-40 border-b border-border/70 bg-background/70 backdrop-blur-xl">
        <div className="mx-auto flex max-w-[1540px] items-center justify-between gap-3 px-4 py-3 lg:px-6">
          <Link href="/dashboard" className="group flex items-center gap-3 rounded-md focus-visible:outline-primary">
            <span aria-hidden className="grid h-9 w-9 place-items-center rounded-md border border-cyan-300/25 bg-cyan-300/10 text-sm font-black text-cyan-200 shadow-glow-cyan">
              H
            </span>
            <span>
              <span className="block text-sm font-semibold tracking-wide text-foreground">HELIUM</span>
              <span className="block text-micro uppercase text-muted-foreground">Institutional Exchange</span>
            </span>
          </Link>
          <nav className="hidden flex-wrap gap-1 text-sm xl:flex" aria-label="Primary navigation">
            {navItems.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "rounded-md px-3 py-2 text-muted-foreground transition hover:bg-white/8 hover:text-foreground",
                  pathname === item.href && "bg-white/10 text-foreground shadow-glow-cyan"
                )}
              >
                {item.label}
              </Link>
            ))}
            {canUseAdmin ? (
              <>
                <Link className={cn("rounded-md px-3 py-2 text-muted-foreground transition hover:bg-white/8 hover:text-foreground", pathname === "/admin" && "bg-white/10 text-foreground")} href="/admin">
                  Admin
                </Link>
                <Link
                  className={cn("rounded-md px-3 py-2 text-muted-foreground transition hover:bg-white/8 hover:text-foreground", pathname === "/admin/reconciliation" && "bg-white/10 text-foreground")}
                  href="/admin/reconciliation"
                >
                  Reconciliation
                </Link>
              </>
            ) : null}
          </nav>
          <div className="flex items-center gap-2">
            <Dropdown label={<span className="max-w-[160px] truncate">{user?.email ?? "Account"}</span>}>
              <DropdownItem onClick={() => router.push("/settings")}>Settings</DropdownItem>
              <DropdownItem onClick={() => logout.mutate()}>Logout</DropdownItem>
            </Dropdown>
            <Button className="xl:hidden" onClick={() => router.push("/dashboard")} size="sm" type="button" variant="secondary">
              Menu
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-[1540px] px-4 py-6 lg:px-6">
        <div className="animate-fade-up">{children}</div>
      </main>
    </div>
  );
}

export function PageHeader({ title, detail }: Readonly<{ title: string; detail?: string }>) {
  return (
    <section className="mb-6 flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="text-micro font-semibold uppercase text-cyan-200/80">HELIUM Exchange</p>
        <h1 className="mt-2 text-display-md text-foreground">{title}</h1>
        {detail ? <p className="mt-2 max-w-3xl text-sm text-muted-foreground">{detail}</p> : null}
      </div>
      <div className="glass-panel rounded-md px-3 py-2 text-xs text-muted-foreground">
        Live risk controls active
      </div>
    </section>
  );
}
