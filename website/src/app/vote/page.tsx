import type { Metadata } from "next";
import { ComingSoon } from "@/components/ComingSoon";

export const metadata: Metadata = { title: "Vote" };

export default function VotePage() {
  return (
    <ComingSoon title="Vote for us">
      Voting on toplists helps new players find the server. Vote rewards will be claimable in game.
    </ComingSoon>
  );
}
