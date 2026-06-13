export default function HomePage() {
  return (
    <main className="min-h-screen bg-slate-950 px-6 py-10 text-slate-100">
      <section className="mx-auto flex max-w-5xl flex-col gap-4">
        <p className="text-sm uppercase tracking-wide text-cyan-300">HELIUM MVP</p>
        <h1 className="text-3xl font-semibold">Project foundation is ready.</h1>
        <p className="max-w-2xl text-sm leading-6 text-slate-300">
          This app shell contains no business logic, API routes, or trading workflows yet.
        </p>
      </section>
    </main>
  );
}

