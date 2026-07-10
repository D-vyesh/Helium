import Link from "next/link";
import { MarketList } from "@/features/market-data/components/market-data-panels";

export default function HomePage() {
  return (
    <main className="min-h-screen bg-background px-4 py-8 text-foreground md:px-8">
      <section className="mx-auto flex max-w-7xl flex-col gap-6">
        <div className="flex flex-col gap-4 border-b border-border pb-6 md:flex-row md:items-end md:justify-between">
          <div>
            <p className="text-micro font-semibold uppercase text-muted-foreground">HELIUM Live Markets</p>
            <h1 className="mt-2 text-3xl font-semibold tracking-normal text-foreground md:text-5xl">BTCUSDT, ETHUSDT, SOLUSDT</h1>
            <p className="mt-3 max-w-2xl text-sm text-muted-foreground">
              Binance-backed spot market data flows through HELIUM backend services, Redis cache, and public HELIUM websocket channels.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link className="rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:opacity-90" href="/trade?symbol=BTCUSDT">
              Open Terminal
            </Link>
            <Link className="rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition hover:bg-white/5" href="/markets">
              Markets
            </Link>
          </div>
        </div>
        <MarketList />
      </section>
    </main>
  );
}
