import Image from "next/image";

/** Item sprites come from RuneLite's public cache dump, which matches the game's item ids. */
export function ItemIcon({ id, name, size = 32 }: { id: number; name: string; size?: number }) {
  return (
    <Image
      src={`https://static.runelite.net/cache/item/icon/${id}.png`}
      alt={name}
      width={size}
      height={size}
      unoptimized
      className="inline-block shrink-0 [image-rendering:pixelated]"
    />
  );
}
