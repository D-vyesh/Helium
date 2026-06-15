export function LoadingState({ label = "Loading" }: Readonly<{ label?: string }>) {
  return (
    <div className="flex min-h-32 items-center justify-center rounded border border-slate-800 bg-slate-950/70 text-sm text-slate-300">
      {label}
    </div>
  );
}

export function EmptyState({ title, detail }: Readonly<{ title: string; detail?: string }>) {
  return (
    <div className="rounded border border-dashed border-slate-700 bg-slate-950/60 p-6 text-sm">
      <p className="font-medium text-slate-100">{title}</p>
      {detail ? <p className="mt-2 text-slate-400">{detail}</p> : null}
    </div>
  );
}

export function ErrorState({ title = "Something went wrong", detail }: Readonly<{ title?: string; detail?: string }>) {
  return (
    <div className="rounded border border-red-900/70 bg-red-950/30 p-4 text-sm text-red-200">
      <p>{title}</p>
      {detail ? <p className="mt-2 text-red-200/80">{detail}</p> : null}
    </div>
  );
}

export function FieldError({ message }: Readonly<{ message?: string }>) {
  if (!message) {
    return null;
  }
  return <p className="mt-1 text-xs text-red-300">{message}</p>;
}
