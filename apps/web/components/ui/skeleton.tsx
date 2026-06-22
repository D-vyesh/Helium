import { cn } from "@/lib/utils/cn";
import type { HTMLAttributes } from "react";

export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "rounded-md bg-[linear-gradient(90deg,hsl(210_20%_100%/.05),hsl(210_20%_100%/.12),hsl(210_20%_100%/.05))] bg-[length:200%_100%] animate-shimmer",
        className
      )}
      {...props}
    />
  );
}
