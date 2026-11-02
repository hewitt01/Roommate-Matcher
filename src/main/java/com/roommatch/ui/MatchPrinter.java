package com.roommatch.ui;

import com.roommatch.agent.RankingOutcome;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.User;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

/** Prints a ranking as plain ASCII text (safe on any Windows console). */
final class MatchPrinter {
    private MatchPrinter() { }

    static void print(PrintStream out, User requester, RankingOutcome outcome, MatchOptions options) {
        if (outcome.isEmptyPool()) {
            out.println("No candidates match your filters. Try relaxing the city, budget or move-in dates.");
            return;
        }
        List<CompatibilityResult> shown = outcome.ranked();
        if (options.top != null && options.top < shown.size()) {
            shown = shown.subList(0, options.top);
        }
        out.printf(Locale.ROOT, "Matches for %s (%s, budget %.0f): %d ranked, %d skipped%n%n",
                requester.getId(), requester.getLocation(), requester.getBudget(),
                outcome.ranked().size(), outcome.skipped().size());
        if (!shown.isEmpty()) {
            out.printf(Locale.ROOT, "%-3s %-10s %-12s %6s %10s %10s %8s%n",
                    "#", "Candidate", "City", "Rent", "You->Them", "Them->You", "Overall");
            int rank = 1;
            for (CompatibilityResult r : shown) {
                User c = r.getCandidate();
                out.printf(Locale.ROOT, "%-3d %-10s %-12s %6.0f %10.1f %10.1f %8.1f%n",
                        rank++, c.getId(), c.getLocation(), c.getBudget(),
                        r.getScoreAtoB(), r.getScoreBtoA(), r.overallScore());
                if (options.rationale && !r.getRationale().isBlank()) {
                    out.println("    " + r.getRationale());
                }
                if (!r.getNotAssessedDimensions().isEmpty()) {
                    out.println("    not assessed (missing data): " + String.join(", ", r.getNotAssessedDimensions()));
                }
            }
            if (shown.size() < outcome.ranked().size()) {
                out.println("\nShowing the top " + shown.size() + " of " + outcome.ranked().size() + ".");
            }
        }
        if (!outcome.skipped().isEmpty()) {
            out.println("\nSkipped:");
            for (RankingOutcome.Skipped s : outcome.skipped()) {
                out.println("  " + s.candidate().getId() + " - " + s.reason());
            }
        }
    }
}
