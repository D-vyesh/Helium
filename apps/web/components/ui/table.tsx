export function DataTable({
  columns,
  rows
}: Readonly<{
  columns: string[];
  rows: React.ReactNode[][];
}>) {
  return (
    <div className="overflow-hidden rounded border border-slate-800">
      <table className="w-full min-w-[640px] border-collapse text-left text-sm">
        <thead className="bg-slate-900 text-xs uppercase text-slate-400">
          <tr>
            {columns.map((column) => (
              <th className="px-4 py-3 font-medium" key={column}>
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800 bg-slate-950">
          {rows.map((row, index) => (
            <tr key={index}>
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
