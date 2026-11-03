package com.roommatch.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.HardFilters;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import com.roommatch.repository.CandidateRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompatibilityAgentRescoreTest {
    private User me;
    private User cand;
    private int scoreCalls;
    private double nextScore;
    private CompatibilityAgent agent;

    private static User user(String id) {
        User u = new User(id, 1000, "Toronto", LocalDate.of(2026, 12, 1));
        LifestyleProfile p = new LifestyleProfile();
        LifestyleProfile.DIMENSIONS.forEach(d -> p.set(d, "x"));
        u.attachLifestyleProfile(p);
        return u;
    }

    @BeforeEach
    void setUp() {
        me = user("me");
        cand = user("c");
        scoreCalls = 0;
        nextScore = 40;
        CandidateRepository repo = new CandidateRepository() {
            @Override public List<User> getCandidates(User u, HardFilters f) { return List.of(cand); }
            @Override public Optional<User> getProfile(String id) { return Optional.empty(); }
        };
        ToolManager tools = new ToolManager();
        tools.register(new ProfileAccessTool(repo));
        agent = new CompatibilityAgent(tools, (r, c) -> {
            scoreCalls++;
            return new CompatibilityResult(c, nextScore, nextScore);
        });
    }

    @Test
    void rankingStampsEachResultWithTheRequestersRevision() {
        agent.rankCandidates(me, HardFilters.none());
        assertEquals(me.getRevision(), agent.getResult(me, cand).orElseThrow().getScoredAtRevision());
        assertTrue(agent.getFreshResult(me, cand).isPresent());
    }

    @Test
    void aResultStopsBeingFreshWhenTheRequesterChanges() {
        agent.rankCandidates(me, HardFilters.none());
        me.applyUpdate(Map.of("budget", "1500"));
        assertTrue(agent.getResult(me, cand).isPresent(), "the old result is still stored");
        assertTrue(agent.getFreshResult(me, cand).isEmpty(), "but it is no longer fresh");
    }

    @Test
    void rescoreReplacesTheStoredResultAndMakesItFreshAgain() {
        agent.rankCandidates(me, HardFilters.none());
        me.applyUpdate(Map.of("budget", "1500"));
        nextScore = 80;
        CompatibilityResult again = agent.rescore(me, cand);
        assertEquals(80, again.getScoreAtoB());
        assertEquals(2, scoreCalls);
        assertSame(again, agent.getFreshResult(me, cand).orElseThrow());
    }

    @Test
    void rescoreWorksForAPairThatWasNeverRanked() {
        CompatibilityResult r = agent.rescore(me, cand);
        assertSame(r, agent.getResult(me, cand).orElseThrow());
        assertEquals(1, scoreCalls);
    }

    @Test
    void rescoreStillRequiresACompleteRequesterProfile() {
        User partial = new User("p", 1000, "Toronto", LocalDate.of(2026, 12, 1));
        LifestyleProfile p = new LifestyleProfile();
        p.set("cleanliness", "tidy");
        partial.attachLifestyleProfile(p);
        assertThrows(ProfileIncompleteException.class, () -> agent.rescore(partial, cand));
        assertEquals(0, scoreCalls);
    }
}
