import { cn } from "@/lib/utils/cn";
import type { InputHTMLAttributes } from "react";

export function Search({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className={cn("relative block", className)}>
      <span className="sr-only">Search</span>
      <span aria-hidden className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">/</span>
      <input
        className="h-10 w-full rounded-md border border-border bg-black/20 pl-9 pr-3 text-sm text-foreground placeholder:text-muted-foreground transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
        type="search"
        {...props}
      />
    </label>
  );
}
