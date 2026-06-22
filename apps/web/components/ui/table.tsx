import { cn } from "@/lib/utils/cn";

export function DataTable({
  columns,
  rows,
  className
}: Readonly<{
  columns: string[];
  rows: React.ReactNode[][];
  className?: string;
}>) {
  return (
    <div className={cn("glass-panel overflow-hidden rounded-lg", className)}>
      <table className="w-full min-w-[640px] border-collapse text-left text-sm">
        <thead className="bg-white/[0.035] text-micro uppercase text-muted-foreground">
          <tr>
            {columns.map((column) => (
              <th className="px-4 py-3 font-semibold" key={column} scope="col">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border/70">
          {rows.map((row, index) => (
            <tr className="transition hover:bg-white/[0.035]" key={index}>
              {row.map((cell, cellIndex) => (
                <td className="px-4 py-3 text-slate-200" key={cellIndex}>
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
