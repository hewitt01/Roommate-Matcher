package com.roommatch.controller;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.RankingOutcome;
import com.roommatch.model.HardFilters;
import com.roommatch.model.User;
import com.roommatch.service.RankingService;
import java.util.Map;
import java.util.Objects;

/**
 * Facade: the single entry point the GUI and CLI use. They never touch the agent, the ranking
 * service or the observer wiring directly, so the inside of the system can change without
 * changing either interface.
 */
public class AgentController {
    private final CompatibilityAgent agent;
    private final RankingService rankingService;

    public AgentController(CompatibilityAgent agent, RankingService rankingService) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.rankingService = Objects.requireNonNull(rankingService, "rankingService");
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

    /** F10. True if the user's last ranking is outdated because they edited their profile since. */
    public boolean isStale(User user) {
        return rankingService.isStale(user);
    }
}
