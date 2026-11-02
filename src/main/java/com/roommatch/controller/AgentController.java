package com.roommatch.controller;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.ElicitationAgent;
import com.roommatch.agent.RankingOutcome;
import com.roommatch.model.HardFilters;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import com.roommatch.service.RankingService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Facade: the single entry point the GUI and CLI use. They never touch the agent, the ranking
 * service or the observer wiring directly, so the inside of the system can change without
 * changing either interface.
 */
public class AgentController {
    private final CompatibilityAgent agent;
    private final RankingService rankingService;
    private final Supplier<ElicitationAgent> elicitationFactory;
    private final Map<String, ElicitationAgent> elicitations = new HashMap<>();

    /** Without elicitation support: elicitation calls fail with IllegalStateException. */
    public AgentController(CompatibilityAgent agent, RankingService rankingService) {
        this(agent, rankingService, () -> {
            throw new IllegalStateException("Profile elicitation is not configured.");
        });
    }

    public AgentController(CompatibilityAgent agent, RankingService rankingService,
                           Supplier<ElicitationAgent> elicitationFactory) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.rankingService = Objects.requireNonNull(rankingService, "rankingService");
        this.elicitationFactory = Objects.requireNonNull(elicitationFactory, "elicitationFactory");
    }

    /** Registers the ranking service as an observer so profile edits can mark results outdated (F10). */
    public void startSession(User user) {
        user.attach(rankingService);
    }

    public void endSession(User user) {
        user.detach(rankingService);
    }

    /** F02. @throws com.roommatch.model.InvalidProfileException if any field is invalid (nothing is saved) */
    public void updateProfile(User user, Map<String, String> fields) {
        user.applyUpdate(fields);
    }

    /** F03. Ranks candidates and records that this user now has fresh results. */
    public RankingOutcome getRankedMatches(User user, HardFilters filters) {
        RankingOutcome outcome = agent.rankCandidates(user, filters);
        rankingService.markRanked(user);
        return outcome;
    }

    // ---- F01: profile elicitation. One conversation per user; a new one replaces any unfinished one. ----

    /** Starts (or restarts) the elicitation conversation and returns the first question. */
    public String runElicitation(User user) {
        ElicitationAgent fresh = elicitationFactory.get();
        String firstQuestion = fresh.start();
        elicitations.put(user.getId(), fresh);
        return firstQuestion;
    }

    public boolean isElicitating(User user) {
        return elicitations.containsKey(user.getId());
    }

    /** Sends the person's answer and returns the agent's next message. */
    public String handleResponse(User user, String answer) {
        return active(user).handleResponse(answer);
    }

    public boolean isElicitationComplete(User user) {
        return active(user).isComplete();
    }

    public String currentElicitationPrompt(User user) {
        return active(user).currentPrompt();
    }

    public Map<String, String> elicitationDraft(User user) {
        return active(user).getDraftAnswers();
    }

    public List<String> elicitationMissing(User user) {
        return active(user).missingDimensions();
    }

    /** Lets the person fix a value themselves instead of trusting the AI's reading. */
    public void correctElicitation(User user, String dimension, String value) {
        active(user).correct(dimension, value);
    }

    /** Saves the profile built so far (complete or partial) to the user and ends the conversation. */
    public LifestyleProfile confirmElicitation(User user) {
        LifestyleProfile saved = active(user).finish(user);
        elicitations.remove(user.getId());
        return saved;
    }

    /** Discards the unfinished conversation. Does nothing if there is none. */
    public void cancelElicitation(User user) {
        elicitations.remove(user.getId());
    }

    private ElicitationAgent active(User user) {
        ElicitationAgent a = elicitations.get(user.getId());
        if (a == null) {
            throw new IllegalStateException("No profile conversation is in progress. Type 'elicit' to start one.");
        }
        return a;
    }

    /** F10. True if the user's last ranking is outdated because they edited their profile since. */
    public boolean isStale(User user) {
        return rankingService.isStale(user);
    }
}
