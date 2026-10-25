package com.roommatch.ui;

import com.roommatch.agent.ProfileIncompleteException;
import com.roommatch.agent.RankingOutcome;
import com.roommatch.config.ConfigException;
import com.roommatch.controller.AgentController;
import com.roommatch.llm.LLMException;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.HardFilters;
import com.roommatch.model.InvalidProfileException;
import com.roommatch.model.User;
import com.roommatch.platform.PlatformException;
import com.roommatch.repository.CandidateRepository;
import java.io.PrintStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Command-line interface. It only parses what the user typed, calls the AgentController
 * (the Facade) and prints results. No ranking or AI logic lives here.
 */
public class RoomMatchCLI {
    /** What the CLI needs from the rest of the system. */
    public record Services(AgentController controller, CandidateRepository repository) { }

    /** Builds the services lazily, so "help" works even before an API key is configured. */
    @FunctionalInterface
    public interface ServiceFactory {
        Services create();
    }

    private static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }

    private static final class MatchOptions {
        String asUser;
        String city;
        Double minBudget;
        Double maxBudget;
        LocalDate from;
        LocalDate to;
        Integer top;
        boolean rationale;
    }

    private final PrintStream out;
    private final PrintStream err;
    private final ServiceFactory factory;

    public RoomMatchCLI(PrintStream out, PrintStream err, ServiceFactory factory) {
        this.out = out;
        this.err = err;
        this.factory = factory;
    }

    /** @return exit code: 0 success, 1 the request could not be completed, 2 bad command line */
    public int run(String[] args) {
        if (args.length == 0) {
            err.println(usage());
            return 2;
        }
        switch (args[0]) {
            case "help", "--help", "-h":
                out.println(usage());
                return 0;
            case "match":
                return match(Arrays.copyOfRange(args, 1, args.length));
            default:
                err.println("Unknown command: " + args[0]);
                err.println(usage());
                return 2;
        }
    }

    private int match(String[] args) {
        MatchOptions options;
        HardFilters filters;
        try {
            options = parseMatchOptions(args);
            filters = buildFilters(options);
        } catch (UsageException | InvalidProfileException e) {
            err.println("Error: " + e.getMessage());
            err.println("Run 'help' to see the options.");
            return 2;
        }

        try {
            Services services = factory.create();
            Optional<User> found = services.repository().getProfile(options.asUser);
            if (found.isEmpty()) {
                err.println("Error: no user with id '" + options.asUser + "' on the platform.");
                return 1;
            }
            User requester = found.get();
            if (!requester.isActive()) {
                err.println("Error: the account '" + options.asUser + "' is deactivated.");
                return 1;
            }
            services.controller().startSession(requester);
            try {
                RankingOutcome outcome = services.controller().getRankedMatches(requester, filters);
                print(requester, outcome, options);
            } finally {
                services.controller().endSession(requester);
            }
            return 0;
        } catch (ProfileIncompleteException e) {
            err.println(e.getMessage() + " Ranking needs a complete profile.");
            return 1;
        } catch (ConfigException | PlatformException e) {
            err.println("Setup problem: " + e.getMessage());
            return 1;
        } catch (LLMException e) {
            err.println("The AI service failed, so no ranking was produced: " + e.getMessage());
            return 1;
        }
    }

    private void print(User requester, RankingOutcome outcome, MatchOptions options) {
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

    private static MatchOptions parseMatchOptions(String[] args) {
        MatchOptions o = new MatchOptions();
        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (flag.equals("--rationale")) {
                o.rationale = true;
                continue;
            }
            if (!List.of("--as", "--city", "--min-budget", "--max-budget", "--from", "--to", "--top").contains(flag)) {
                throw new UsageException("Unknown option: " + flag);
            }
            if (i + 1 >= args.length) {
                throw new UsageException("Option " + flag + " needs a value.");
            }
            String value = args[++i];
            switch (flag) {
                case "--as" -> o.asUser = value;
                case "--city" -> o.city = value;
                case "--min-budget" -> o.minBudget = parseNumber(flag, value);
                case "--max-budget" -> o.maxBudget = parseNumber(flag, value);
                case "--from" -> o.from = parseDate(flag, value);
                case "--to" -> o.to = parseDate(flag, value);
                case "--top" -> {
                    double n = parseNumber(flag, value);
                    if (n < 1 || n != Math.floor(n)) {
                        throw new UsageException("--top must be a whole number of at least 1.");
                    }
                    o.top = (int) n;
                }
                default -> throw new UsageException("Unknown option: " + flag);
            }
        }
        if (o.asUser == null || o.asUser.isBlank()) {
            throw new UsageException("Missing --as <userId> (which platform user are you?).");
        }
        return o;
    }

    private static HardFilters buildFilters(MatchOptions o) {
        HardFilters f = HardFilters.none();
        if (o.city != null) {
            f = f.withCity(o.city);
        }
        if (o.minBudget != null || o.maxBudget != null) {
            f = f.withBudgetRange(o.minBudget, o.maxBudget);
        }
        if (o.from != null || o.to != null) {
            f = f.withMoveInWindow(o.from, o.to);
        }
        return f;
    }

    private static double parseNumber(String flag, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new UsageException(flag + " needs a number, got '" + value + "'.");
        }
    }

    private static LocalDate parseDate(String flag, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new UsageException(flag + " needs a date like 2026-12-01, got '" + value + "'.");
        }
    }

    static String usage() {
        return String.join(System.lineSeparator(),
                "RoomMatch AI - command line",
                "",
                "Commands:",
                "  match --as <userId> [options]   Rank compatible roommates for a platform user",
                "  help                            Show this message",
                "",
                "Options for match:",
                "  --as <userId>        Who you are (a user id from data/platform_users.json), required",
                "  --city <name>        Only candidates in this city",
                "  --min-budget <n>     Only candidates whose rent budget is at least n",
                "  --max-budget <n>     Only candidates whose rent budget is at most n",
                "  --from <yyyy-MM-dd>  Only candidates available on or after this date",
                "  --to <yyyy-MM-dd>    Only candidates available on or before this date",
                "  --top <n>            Show only the best n matches",
                "  --rationale          Show the AI's reasoning under each match",
                "",
                "Example:",
                "  match --as p001 --city Toronto --top 3 --rationale");
    }
}
