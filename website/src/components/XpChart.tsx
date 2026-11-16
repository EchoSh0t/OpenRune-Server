import { compactXp, formatNumber } from "@/lib/format";

const WIDTH = 640;
const HEIGHT = 220;
const PAD = { top: 16, right: 16, bottom: 28, left: 56 };

export type ChartSeries = { label: string; colour: string; points: { day: string; xp: number }[] };

/** A server-rendered line chart of total XP per day, one line per series. */
export function XpChart({ series }: { series: ChartSeries[] }) {
  const days = [...new Set(series.flatMap((s) => s.points.map((p) => p.day)))].sort();
  const values = series.flatMap((s) => s.points.map((p) => p.xp));
  if (days.length < 2 || values.length === 0) {
    return (
      <p className="py-10 text-center text-sm text-mist-dim">
        Not enough history yet. The tracker takes a snapshot every hour, so check back tomorrow.
      </p>
    );
  }

  let min = Math.min(...values);
  let max = Math.max(...values);
  if (min === max) {
    min = Math.max(0, min - 1);
    max += 1;
  }
  const plotW = WIDTH - PAD.left - PAD.right;
  const plotH = HEIGHT - PAD.top - PAD.bottom;
  const x = (day: string) => PAD.left + (days.indexOf(day) / (days.length - 1)) * plotW;
  const y = (xp: number) => PAD.top + plotH - ((xp - min) / (max - min)) * plotH;
  const ticks = [0, 0.5, 1].map((t) => min + (max - min) * t);
  const labelEvery = Math.max(1, Math.ceil(days.length / 6));

  return (
    <figure>
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="h-auto w-full" role="img" aria-label="XP over time">
        {ticks.map((t) => (
          <g key={t}>
            <line x1={PAD.left} x2={WIDTH - PAD.right} y1={y(t)} y2={y(t)} stroke="#232e50" strokeDasharray="3 4" />
            <text x={PAD.left - 8} y={y(t) + 4} textAnchor="end" fontSize="11" fill="#9ba8c6">
              {compactXp(Math.round(t))}
            </text>
          </g>
        ))}
        {days.map((day, i) =>
          i % labelEvery === 0 || i === days.length - 1 ? (
            <text key={day} x={x(day)} y={HEIGHT - 8} textAnchor="middle" fontSize="11" fill="#9ba8c6">
              {day.slice(5)}
            </text>
          ) : null,
        )}
        {series.map((s) => (
          <g key={s.label}>
            <polyline
              fill="none"
              stroke={s.colour}
              strokeWidth="2.5"
              strokeLinejoin="round"
              points={s.points.map((p) => `${x(p.day)},${y(p.xp)}`).join(" ")}
            />
            {s.points.map((p) => (
              <circle key={p.day} cx={x(p.day)} cy={y(p.xp)} r="3" fill={s.colour}>
                <title>{`${s.label} ${p.day}: ${formatNumber(p.xp)} xp`}</title>
              </circle>
            ))}
          </g>
        ))}
      </svg>
      {series.length > 1 && (
        <figcaption className="mt-2 flex justify-center gap-5 text-xs text-mist-dim">
          {series.map((s) => (
            <span key={s.label} className="flex items-center gap-1.5">
              <span className="inline-block size-2.5 rounded-full" style={{ backgroundColor: s.colour }} />
              {s.label}
            </span>
          ))}
        </figcaption>
      )}
    </figure>
  );
}
