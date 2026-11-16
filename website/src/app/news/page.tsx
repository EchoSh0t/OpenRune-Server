import type { Metadata } from "next";
import { NewsCard } from "@/components/NewsCard";
import { PageHeader } from "@/components/PageHeader";
import { getAllPosts } from "@/lib/news";

export const metadata: Metadata = { title: "News" };

export default function NewsPage() {
  const posts = getAllPosts();
  return (
    <>
      <PageHeader title="News & updates">Patch notes and announcements, newest first.</PageHeader>
      <div className="mx-auto max-w-6xl px-4 py-12">
        {posts.length === 0 ? (
          <p className="text-mist-dim">No news yet. Check back soon.</p>
        ) : (
          <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {posts.map((post) => (
              <NewsCard key={post.slug} post={post} />
            ))}
          </div>
        )}
      </div>
    </>
  );
}
