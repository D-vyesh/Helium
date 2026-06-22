import { Card, CardContent } from "./card";
import { Skeleton } from "./skeleton";

export function LoadingState({ label = "Loading" }: Readonly<{ label?: string }>) {
  return (
    <Card className="min-h-32">
      <CardContent className="flex h-full min-h-32 flex-col justify-center gap-3">
        <Skeleton className="h-4 w-28" />
        <Skeleton className="h-12 w-full" />
        <span className="sr-only">{label}</span>
      </CardContent>
    </Card>
  );
}

export function EmptyState({ title, detail }: Readonly<{ title: string; detail?: string }>) {
  return (
    <Card className="border-dashed">
      <CardContent className="p-6 text-sm">
        <p className="font-semibold text-foreground">{title}</p>
        {detail ? <p className="mt-2 text-muted-foreground">{detail}</p> : null}
      </CardContent>
    </Card>
  );
}

export function ErrorState({ title = "Something went wrong", detail }: Readonly<{ title?: string; detail?: string }>) {
  return (
    <div className="rounded-lg border border-red-400/25 bg-red-950/30 p-4 text-sm text-red-100" role="alert">
      <p className="font-semibold">{title}</p>
      {detail ? <p className="mt-2 text-red-100/80">{detail}</p> : null}
    </div>
  );
}

export function FieldError({ message }: Readonly<{ message?: string }>) {
  if (!message) {
    return null;
  }
  return <p className="mt-1 text-xs text-red-300" role="alert">{message}</p>;
}
