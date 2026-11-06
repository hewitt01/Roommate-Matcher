package com.roommatch.ui;

import com.roommatch.agent.ProfileIncompleteException;
import com.roommatch.agent.RankingOutcome;
import com.roommatch.config.ConfigException;
import com.roommatch.controller.AgentController;
import com.roommatch.llm.LLMException;
import com.roommatch.model.HardFilters;
import com.roommatch.model.InvalidProfileException;
import com.roommatch.model.User;
import com.roommatch.platform.PlatformException;
import com.roommatch.repository.CandidateRepository;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

    /** Returned by {@link #run} when a window is open and the program must keep running. */
    public static final int KEEP_RUNNING = -1;

    private final PrintStream out;
    private final PrintStream err;
    private final InputStream in;
    private final ServiceFactory factory;

    public RoomMatchCLI(PrintStream out, PrintStream err, ServiceFactory factory) {
        this(out, err, System.in, factory);
    }

    public RoomMatchCLI(PrintStream out, PrintStream err, InputStream in, ServiceFactory factory) {
        this.out = out;
        this.err = err;
        this.in = in;
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
            case "shell":
                return shell();
            case "gui":
                return gui();
            default:
                err.println("Unknown command: " + args[0]);
                err.println(usage());
                return 2;
        }
    }

    private int gui() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            err.println("This computer has no display, so the window cannot open. Use 'shell' instead.");
            return 1;
        }
        try {
            RoomMatchGUI.launch(factory.create());
            return KEEP_RUNNING;
        } catch (ConfigException | PlatformException e) {
            err.println("Setup problem: " + e.getMessage());
            return 1;
        }
    }

    private int shell() {
        try {
            Services services = factory.create();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return new InteractiveShell(reader, out, err, services).run();
        } catch (ConfigException | PlatformException e) {
            err.println("Setup problem: " + e.getMessage());
            return 1;
        }
    }

    private int match(String[] args) {
        MatchOptions options;
        HardFilters filters;
        try {
            options = MatchOptions.parse(args, true);
            filters = options.toFilters();
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
                MatchPrinter.print(out, requester, outcome, options);
            } finally {
                services.controller().endSession(requester);
            }
            return 0;
        } catch (ProfileIncompleteException e) {
            err.println(e.getMessage() + " Ranking needs a complete profile. "
                    + "Use the interactive mode ('shell', then 'login' and 'elicit') to complete it.");
            return 1;
        } catch (ConfigException | PlatformException e) {
            err.println("Setup problem: " + e.getMessage());
            return 1;
        } catch (LLMException e) {
            err.println("The AI service failed, so no ranking was produced: " + e.getMessage());
            return 1;
        }
    }

    static String usage() {
        return String.join(System.lineSeparator(),
                "RoomMatch AI - command line",
                "",
                "Commands:",
                "  gui                             Open the graphical window",
                "  shell                           Interactive mode: log in, build your profile, match, explain, friction, ask, shortlist, edit",
                "  match --as <userId> [options]   One-shot: rank compatible roommates for a platform user",
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
                "Examples:",
                "  match --as p001 --city Toronto --top 3 --rationale",
                "  shell");
    }
}
