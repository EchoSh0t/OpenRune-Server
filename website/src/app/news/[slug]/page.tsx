import type { Metadata } from "next";
import { cacheLife } from "next/cache";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { formatDate, getAllPosts, getPost, renderMarkdown } from "@/lib/news";

export function generateStaticParams() {
  return getAllPosts().map((post) => ({ slug: post.slug }));
}

export async function generateMetadata({ params }: PageProps<"/news/[slug]">): Promise<Metadata> {
  const post = getPost((await params).slug);
  return post ? { title: post.title, description: post.summary } : {};
}

export default function NewsPostPage({ params }: PageProps<"/news/[slug]">) {
  return (
    <article className="mx-auto max-w-3xl px-4 py-12">
      <Link href="/news" className="text-sm font-semibold text-star-300 hover:text-accent-hover">
        &larr; All news
      </Link>
      <Suspense fallback={<div className="panel mt-6 h-96 animate-pulse" />}>
        {params.then(({ slug }) => (
          <Post slug={slug} />
        ))}
      </Suspense>
    </article>
  );
}

async function Post({ slug }: { slug: string }) {
  "use cache";
  cacheLife("max");
  const post = getPost(slug);
  if (!post) notFound();

  return (
    <>
      <time className="mt-6 block text-xs font-semibold tracking-widest text-mist-dim uppercase">
        {formatDate(post.date)}
      </time>
      <h1 className="heading mt-2 text-4xl font-black">{post.title}</h1>
      <div
        className="panel prose-osrs mt-8 p-6 sm:p-8"
        dangerouslySetInnerHTML={{ __html: renderMarkdown(post.body) }}
      />
    </>
  );
}
