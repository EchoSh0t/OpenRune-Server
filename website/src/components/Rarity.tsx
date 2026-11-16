export function Rarity({ chance, text }: { chance: number; text: string }) {
  const colour =
    chance >= 1
      ? "text-mist"
      : chance >= 1 / 25
        ? "text-success"
        : chance >= 1 / 100
          ? "text-star-300"
          : chance >= 1 / 1000
            ? "text-amber-300"
            : "text-danger";
  return <span className={`font-semibold ${colour}`}>{text}</span>;
}
