package com.roommatch.ui;

import com.roommatch.agent.ElicitationException;
import com.roommatch.agent.ExplanationException;
import com.roommatch.agent.MatchExplanation;
import com.roommatch.agent.ProfileIncompleteException;
import com.roommatch.agent.RankingOutcome;
import com.roommatch.agent.ScoringException;
import com.roommatch.controller.AgentController;
import com.roommatch.llm.LLMException;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.InvalidProfileException;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import com.roommatch.repository.CandidateRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Interactive mode: a logged-in session that remembers who you are, your last ranking, and any
 * unfinished profile conversation. Like the one-shot CLI it only talks to the AgentController.
 * While a profile conversation is running, every line you type is an answer, except lines that
 * start with "/".
 */
final class InteractiveShell {
    private final BufferedReader in;
    private final PrintStream out;
    private final PrintStream err;
    private final AgentController controller;
    private final CandidateRepository repository;

    private User user;
    private MatchOptions lastOptions;
    private RankingOutcome lastOutcome;

    InteractiveShell(BufferedReader in, PrintStream out, PrintStream err, RoomMatchCLI.Services services) {
        this.in = in;
        this.out = out;
        this.err = err;
        this.controller = services.controller();
        this.repository = services.repository();
    }

    int run() {
        out.println("RoomMatch AI interactive mode. Type 'help' for commands, 'quit' to leave.");
        try {
            while (true) {
                out.print(user != null && controller.isElicitating(user) ? "you> " : "roommatch> ");
                out.flush();
                String line = readLine();
                if (line == null) {
                    out.println();
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    if (user != null && controller.isElicitating(user)) {
                        handleElicitationLine(line);
                    } else if (!handleCommand(line)) {
                        break;
                    }
                } catch (UsageException | InvalidProfileException | IllegalStateException e) {
                    err.println("Error: " + e.getMessage());
                } catch (ProfileIncompleteException e) {
                    err.println(e.getMessage() + " Type 'elicit' to complete it.");
                } catch (ElicitationException e) {
                    err.println(e.getMessage());
                } catch (ScoringException | ExplanationException e) {
                    err.println("Could not explain this match: " + e.getMessage());
                } catch (LLMException e) {
                    err.println("The AI service failed: " + e.getMessage() + " Nothing was lost, you can try again.");
                }
            }
        } finally {
            leave();
        }
        out.println("Goodbye.");
        return 0;
    }

    // ---------------------------------------------------------------- commands

    /** @return false when the person asked to quit */
    private boolean handleCommand(String line) {
        String[] parts = line.split("\\s+");
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (command.equals("quit") || command.equals("exit")) {
            return false;
        }
        switch (command) {
            case "help" -> out.println(helpText());
            case "login" -> login(parts);
            case "whoami" -> whoAmI();
            case "edit" -> edit(line.substring(parts[0].length()).trim());
            case "elicit" -> elicit();
            case "match" -> match(Arrays.copyOfRange(parts, 1, parts.length));
            case "results" -> results();
            case "explain" -> explain(parts);
            default -> err.println("Unknown command: " + parts[0] + ". Type 'help'.");
        }
        return true;
    }

    private void login(String[] parts) {
        if (parts.length != 2) {
            throw new UsageException("Usage: login <userId>");
        }
        Optional<User> found = repository.getProfile(parts[1]);
        if (found.isEmpty()) {
            err.println("Error: no user with id '" + parts[1] + "' on the platform.");
            return;
        }
        if (!found.get().isActive()) {
            err.println("Error: the account '" + parts[1] + "' is deactivated.");
            return;
        }
        leave();
        user = found.get();
        controller.startSession(user);
        lastOptions = null;
        lastOutcome = null;
        out.println("Logged in as " + user.getId() + ".");
        whoAmI();
    }

    private void whoAmI() {
        requireUser();
        out.printf(Locale.ROOT, "User %s: %s, budget %.0f, move-in %s%n",
                user.getId(), user.getLocation(), user.getBudget(), user.getMoveInDate());
        Optional<LifestyleProfile> profile = user.getLifestyleProfile();
        if (profile.isEmpty() || profile.get().asMap().isEmpty()) {
            out.println("Lifestyle profile: not set. Type 'elicit' to build it.");
            return;
        }
        LifestyleProfile p = profile.get();
        out.println(p.isComplete() ? "Lifestyle profile: complete"
                : "Lifestyle profile: incomplete (missing: " + String.join(", ", p.missingDimensions()) + ")");
        p.asMap().forEach((d, v) -> out.println("  " + d + ": " + v));
    }

    /** F02: edit budget, location or move-in date. */
    private void edit(String assignments) {
        requireUser();
        controller.updateProfile(user, parseAssignments(assignments));
        out.println("Profile updated.");
        offerReRank();
    }

    private void match(String[] args) {
        requireUser();
        runMatch(MatchOptions.parse(args, false));
    }

    private void runMatch(MatchOptions options) {
        RankingOutcome outcome = controller.getRankedMatches(user, options.toFilters());
        lastOptions = options;
        lastOutcome = outcome;
        MatchPrinter.print(out, user, outcome, options);
    }

    /** Shows the last ranking again, loudly marked if the profile changed since it was produced (F10). */
    private void results() {
        requireUser();
        if (lastOutcome == null) {
            out.println("No results yet. Type 'match' to rank candidates.");
            return;
        }
        if (controller.isStale(user)) {
            out.println("WARNING: these results are OUT OF DATE. They were produced before your latest "
                    + "profile changes. Type 'match' to refresh them.");
        }
        MatchPrinter.print(out, user, lastOutcome, lastOptions);
    }

    /** F05: "Why this match?" for a candidate from the last ranking, picked by rank number or id. */
    private void explain(String[] parts) {
        requireUser();
        if (parts.length != 2) {
            throw new UsageException("Usage: explain <rank number or candidate id>");
        }
        if (lastOutcome == null) {
            out.println("No results yet. Type 'match' first.");
            return;
        }
        CompatibilityResult chosen = pick(parts[1]);
        MatchExplanation explanation = controller.getExplanation(user, chosen.getCandidate());
        CompatibilityResult r = explanation.result();
        out.println("Why " + r.getCandidate().getId() + " matched:");
        if (explanation.rescored()) {
            out.println("  (Your profile changed after that ranking, so this match was re-scored first.)");
        }
        out.printf(Locale.ROOT, "  You->Them %.1f | Them->You %.1f | Overall %.1f%n",
                r.getScoreAtoB(), r.getScoreBtoA(), r.overallScore());
        out.println(TextWrap.wrap(explanation.text(), 78, "  "));
        if (!r.getNotAssessedDimensions().isEmpty()) {
            out.println("  Not compared (missing data): " + String.join(", ", r.getNotAssessedDimensions()));
        }
    }

    private CompatibilityResult pick(String reference) {
        var ranked = lastOutcome.ranked();
        try {
            int number = Integer.parseInt(reference);
            if (number < 1 || number > ranked.size()) {
                throw new UsageException("There is no match number " + number
                        + " (your last ranking has " + ranked.size() + ").");
            }
            return ranked.get(number - 1);
        } catch (NumberFormatException e) {
            return ranked.stream().filter(r -> r.getCandidate().getId().equals(reference)).findFirst()
                    .orElseThrow(() -> new UsageException("'" + reference + "' is not in your last ranking."));
        }
    }

    /** F10: after any change to the profile, offer to re-rank if earlier results are now outdated. */
    private void offerReRank() {
        if (!controller.isStale(user)) {
            return;
        }
        out.print("Your profile changed, so your last match results are out of date. Re-run matching now? [y/n] ");
        out.flush();
        String answer = readLine();
        if (answer != null && (answer.trim().equalsIgnoreCase("y") || answer.trim().equalsIgnoreCase("yes"))) {
            runMatch(lastOptions);
        } else {
            out.println();
            out.println("OK. Your existing results stay marked as out of date until you run 'match' again.");
        }
    }

    // ------------------------------------------------- F01: profile conversation

    private void elicit() {
        requireUser();
        String firstQuestion = controller.runElicitation(user);
        out.println("Let's build your lifestyle profile. Answer in your own words.");
        out.println("(/show review answers, /correct <dimension> <text> fix one, /confirm save, "
                + "/stop save what we have, /cancel discard)");
        out.println("Agent: " + firstQuestion);
    }

    private void handleElicitationLine(String line) {
        if (line.startsWith("/")) {
            handleSlashCommand(line);
            return;
        }
        String reply = controller.handleResponse(user, line);
        out.println("Agent: " + reply);
        if (controller.isElicitationComplete(user)) {
            out.println("(Type /confirm to save, or /correct <dimension> <text> to fix something.)");
        }
    }

    private void handleSlashCommand(String line) {
        String[] parts = line.split("\\s+", 3);
        switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "/confirm", "/stop" -> {
                LifestyleProfile saved = controller.confirmElicitation(user);
                out.println(saved.isComplete() ? "Profile saved."
                        : "Progress saved. Still missing: " + String.join(", ", saved.missingDimensions())
                                + ". Ranking needs all of them, so type 'elicit' again to finish.");
                offerReRank();
            }
            case "/cancel" -> {
                controller.cancelElicitation(user);
                out.println("Discarded. Your saved profile is unchanged.");
            }
            case "/show" -> {
                controller.elicitationDraft(user).forEach((d, v) -> out.println("  " + d + ": " + v));
                out.println("  not yet recorded: " + controller.elicitationMissing(user));
            }
            case "/correct" -> {
                if (parts.length < 3) {
                    throw new UsageException("Usage: /correct <dimension> <your words>");
                }
                controller.correctElicitation(user, parts[1], parts[2]);
                out.println("Updated " + parts[1] + ".");
                out.println(controller.isElicitationComplete(user)
                        ? "Everything is filled in. Type /confirm to save."
                        : "Agent: " + controller.currentElicitationPrompt(user));
            }
            default -> throw new UsageException("Unknown profile command '" + parts[0]
                    + "'. Use /show, /correct, /confirm, /stop or /cancel.");
        }
    }

    // ----------------------------------------------------------------- helpers

    private static Map<String, String> parseAssignments(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (!text.isBlank()) {
            String key = null;
            for (String token : text.split("\\s+")) {
                int eq = token.indexOf('=');
                if (eq > 0) {
                    key = token.substring(0, eq);
                    fields.put(key, token.substring(eq + 1));
                } else if (key != null) {
                    fields.put(key, fields.get(key) + " " + token);   // value with spaces, e.g. location=New York
                } else {
                    throw new UsageException("Expected key=value, got '" + token + "'.");
                }
            }
        }
        if (fields.isEmpty()) {
            throw new UsageException("Usage: edit budget=1200 location=Vaughan moveInDate=2027-01-15");
        }
        return fields;
    }

    private void requireUser() {
        if (user == null) {
            throw new UsageException("Log in first: login <userId>");
        }
    }

    private void leave() {
        if (user != null) {
            controller.cancelElicitation(user);
            controller.endSession(user);
            user = null;
        }
    }

    private String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private static String helpText() {
        return String.join(System.lineSeparator(),
                "Commands:",
                "  login <userId>            Log in as a platform user (try p001, or p006 to build a profile)",
                "  whoami                    Show your details and lifestyle profile",
                "  elicit                    Build your lifestyle profile through a conversation",
                "  edit key=value ...        Change budget, location or moveInDate",
                "  match [options]           Rank compatible roommates (--city, --min-budget, --max-budget,",
                "                            --from, --to, --top, --rationale)",
                "  results                   Show your last ranking (marked if it is out of date)",
                "  explain <n or id>         Why a match from your last ranking scored as it did",
                "  help                      Show this message",
                "  quit                      Leave");
    }
}
