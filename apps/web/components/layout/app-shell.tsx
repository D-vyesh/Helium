"use client";

import { useAuthStore } from "@/features/auth/store";
import { heliumApi } from "@/lib/api/client";
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
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <header className="border-b border-slate-800 bg-slate-950/95">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <Link href="/dashboard" className="text-lg font-semibold tracking-wide text-cyan-300">
            HELIUM
          </Link>
          <nav className="flex flex-wrap gap-1 text-sm">
            {navItems.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={`rounded px-3 py-2 ${pathname === item.href ? "bg-cyan-400 text-slate-950" : "text-slate-300 hover:bg-slate-900"}`}
              >
                {item.label}
              </Link>
            ))}
            {canUseAdmin ? (
              <>
                <Link className={`rounded px-3 py-2 ${pathname === "/admin" ? "bg-cyan-400 text-slate-950" : "text-slate-300 hover:bg-slate-900"}`} href="/admin">
                  Admin
                </Link>
                <Link
                  className={`rounded px-3 py-2 ${pathname === "/admin/reconciliation" ? "bg-cyan-400 text-slate-950" : "text-slate-300 hover:bg-slate-900"}`}
                  href="/admin/reconciliation"
                >
                  Reconciliation
                </Link>
              </>
            ) : null}
          </nav>
          <div className="flex items-center gap-3 text-sm text-slate-300">
            <span>{user?.email}</span>
            <button className="rounded border border-slate-700 px-3 py-2 hover:bg-slate-900" onClick={() => logout.mutate()} type="button">
              Logout
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6">{children}</main>
    </div>
  );
}

export function PageHeader({ title, detail }: Readonly<{ title: string; detail?: string }>) {
  return (
    <section className="mb-6">
      <h1 className="text-2xl font-semibold text-slate-50">{title}</h1>
      {detail ? <p className="mt-2 max-w-3xl text-sm text-slate-400">{detail}</p> : null}
    </section>
  );
}
