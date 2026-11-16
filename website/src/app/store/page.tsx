import type { Metadata } from "next";
import { ComingSoon } from "@/components/ComingSoon";

export const metadata: Metadata = { title: "Store" };

export default function StorePage() {
  return (
    <ComingSoon title="Store">
      Support the server&apos;s hosting costs. Nothing in the store will ever give a gameplay advantage.
    </ComingSoon>
  );
}
