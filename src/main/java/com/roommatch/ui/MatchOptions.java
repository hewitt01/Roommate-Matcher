package com.roommatch.ui;

import com.roommatch.model.HardFilters;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** The options of the "match" command, shared by the one-shot CLI and the interactive shell. */
class MatchOptions {
    private static final List<String> VALUE_FLAGS =
            List.of("--as", "--city", "--min-budget", "--max-budget", "--from", "--to", "--top");

    String asUser;
    String city;
    Double minBudget;
    Double maxBudget;
    LocalDate from;
    LocalDate to;
    Integer top;
    boolean rationale;

    /**
     * @param oneShot true for "match --as <id> ..." (which requires --as); false inside the shell,
     *                where the user is already logged in and --as is not accepted
     */
    static MatchOptions parse(String[] args, boolean oneShot) {
        MatchOptions o = new MatchOptions();
        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (flag.equals("--rationale")) {
                o.rationale = true;
                continue;
            }
            if (!VALUE_FLAGS.contains(flag) || (flag.equals("--as") && !oneShot)) {
                throw new UsageException("Unknown option: " + flag
                        + (flag.equals("--as") ? " (use 'login <userId>' instead)" : ""));
            }
            if (i + 1 >= args.length) {
                throw new UsageException("Option " + flag + " needs a value.");
            }
            String value = args[++i];
            switch (flag) {
                case "--as" -> o.asUser = value;
                case "--city" -> o.city = value;
                case "--min-budget" -> o.minBudget = number(flag, value);
                case "--max-budget" -> o.maxBudget = number(flag, value);
                case "--from" -> o.from = date(flag, value);
                case "--to" -> o.to = date(flag, value);
                default -> {
                    double n = number(flag, value);
                    if (n < 1 || n != Math.floor(n)) {
                        throw new UsageException("--top must be a whole number of at least 1.");
                    }
                    o.top = (int) n;
                }
            }
        }
        if (oneShot && (o.asUser == null || o.asUser.isBlank())) {
            throw new UsageException("Missing --as <userId> (which platform user are you?).");
        }
        return o;
    }

    /** @throws com.roommatch.model.InvalidProfileException if the filters contradict each other */
    HardFilters toFilters() {
        HardFilters f = HardFilters.none();
        if (city != null) {
            f = f.withCity(city);
        }
        if (minBudget != null || maxBudget != null) {
            f = f.withBudgetRange(minBudget, maxBudget);
        }
        if (from != null || to != null) {
            f = f.withMoveInWindow(from, to);
        }
        return f;
    }

    private static double number(String flag, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new UsageException(flag + " needs a number, got '" + value + "'.");
        }
    }

    private static LocalDate date(String flag, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new UsageException(flag + " needs a date like 2026-12-01, got '" + value + "'.");
        }
    }
}
