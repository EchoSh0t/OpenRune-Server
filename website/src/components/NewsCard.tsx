import Link from "next/link";
import { formatDate, type NewsPost } from "@/lib/news";

export function NewsCard({ post }: { post: NewsPost }) {
  return (
    <Link
      href={`/news/${post.slug}`}
      className="panel group flex flex-col p-5 transition hover:border-star-700 hover:bg-night-850"
    >
      <time className="text-xs font-semibold tracking-widest text-mist-dim uppercase">
        {formatDate(post.date)}
      </time>
      <h3 className="mt-2 font-display text-lg font-bold text-star-300 group-hover:text-accent-hover">
        {post.title}
      </h3>
      <p className="mt-2 flex-1 text-sm text-mist-dim">{post.summary}</p>
      <span className="mt-4 text-sm font-semibold text-accent">Read more &rarr;</span>
    </Link>
  );
}
