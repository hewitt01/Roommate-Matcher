package com.roommatch.controller;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.ElicitationAgent;
import com.roommatch.agent.ExplanationGenerator;
import com.roommatch.agent.FrictionMapGenerator;
import com.roommatch.agent.FrictionMapView;
import com.roommatch.agent.MatchAnswer;
import com.roommatch.agent.MatchExplanation;
import com.roommatch.agent.MatchQuestionAnswerer;
import com.roommatch.agent.RankingOutcome;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.FrictionMap;
import com.roommatch.model.HardFilters;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.Shortlist.AddResult;
import com.roommatch.model.User;
import com.roommatch.service.RankingService;
import com.roommatch.service.ShortlistService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Facade: the single entry point the GUI and CLI use. They never touch the agents, the ranking
 * service or the observer wiring directly, so the inside of the system can change without
 * changing either interface. Optional features are switched on through the Builder; calling a
 * feature that was not configured fails with a clear IllegalStateException.
 */
public class AgentController {
    private final CompatibilityAgent agent;
    private final RankingService rankingService;
    private final Supplier<ElicitationAgent> elicitationFactory;
    private final ExplanationGenerator explanationGenerator;
    private final FrictionMapGenerator frictionMapGenerator;
    private final MatchQuestionAnswerer questionAnswerer;
    private final ShortlistService shortlistService;
    private final Map<String, ElicitationAgent> elicitations = new HashMap<>();

    /** Collects the optional parts. Start with AgentController.builder(agent, rankingService). */
    public static final class Builder {
        private final CompatibilityAgent agent;
        private final RankingService rankingService;
        private Supplier<ElicitationAgent> elicitation;
        private ExplanationGenerator explanations;
        private FrictionMapGenerator frictionMaps;
        private MatchQuestionAnswerer questions;
        private ShortlistService shortlists;

        private Builder(CompatibilityAgent agent, RankingService rankingService) {
            this.agent = agent;
            this.rankingService = rankingService;
        }

        public Builder elicitation(Supplier<ElicitationAgent> factory) { this.elicitation = factory; return this; }
        public Builder explanations(ExplanationGenerator g) { this.explanations = g; return this; }
        public Builder frictionMaps(FrictionMapGenerator g) { this.frictionMaps = g; return this; }
        public Builder questions(MatchQuestionAnswerer a) { this.questions = a; return this; }
        public Builder shortlists(ShortlistService s) { this.shortlists = s; return this; }

        public AgentController build() {
            return new AgentController(agent, rankingService, elicitation, explanations,
                    frictionMaps, questions, shortlists);
        }
    }

    public static Builder builder(CompatibilityAgent agent, RankingService rankingService) {
        return new Builder(agent, rankingService);
    }

    public AgentController(CompatibilityAgent agent, RankingService rankingService) {
        this(agent, rankingService, null, null, null, null, null);
    }

    public AgentController(CompatibilityAgent agent, RankingService rankingService,
                           Supplier<ElicitationAgent> elicitationFactory) {
        this(agent, rankingService, elicitationFactory, null, null, null, null);
    }

    public AgentController(CompatibilityAgent agent, RankingService rankingService,
                           Supplier<ElicitationAgent> elicitationFactory,
                           ExplanationGenerator explanationGenerator) {
        this(agent, rankingService, elicitationFactory, explanationGenerator, null, null, null);
    }

    public AgentController(CompatibilityAgent agent, RankingService rankingService,
                           Supplier<ElicitationAgent> elicitationFactory,
                           ExplanationGenerator explanationGenerator,
                           FrictionMapGenerator frictionMapGenerator) {
        this(agent, rankingService, elicitationFactory, explanationGenerator, frictionMapGenerator, null, null);
    }

    private AgentController(CompatibilityAgent agent, RankingService rankingService,
                            Supplier<ElicitationAgent> elicitationFactory,
                            ExplanationGenerator explanationGenerator,
                            FrictionMapGenerator frictionMapGenerator,
                            MatchQuestionAnswerer questionAnswerer,
                            ShortlistService shortlistService) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.rankingService = Objects.requireNonNull(rankingService, "rankingService");
        this.elicitationFactory = elicitationFactory;
        this.explanationGenerator = explanationGenerator;
        this.frictionMapGenerator = frictionMapGenerator;
        this.questionAnswerer = questionAnswerer;
        this.shortlistService = shortlistService;
    }

    /** Registers the ranking service as an observer (F10) and restores the user's saved shortlist (F09). */
    public void startSession(User user) {
        user.attach(rankingService);
        if (shortlistService != null) {
            shortlistService.restore(user);
        }
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

    // ---- results for one pair: they all use the same freshness rule ----

    private record Scored(CompatibilityResult result, boolean rescored) { }

    /** The stored result if the user has not changed since it was computed, otherwise a re-scored one. */
    private Scored freshOrRescore(User user, User candidate) {
        Optional<CompatibilityResult> fresh = agent.getFreshResult(user, candidate);
        return new Scored(fresh.orElseGet(() -> agent.rescore(user, candidate)), fresh.isEmpty());
    }

    private static <T> T configured(T feature, String name) {
        if (feature == null) {
            throw new IllegalStateException(name + " is not configured.");
        }
        return feature;
    }

    /**
     * F05. Explains why this candidate matched. Outdated results are re-scored first, so the
     * explanation is never built on old numbers. A finished explanation is kept on the result.
     */
    public MatchExplanation getExplanation(User user, User candidate) {
        ExplanationGenerator generator = configured(explanationGenerator, "Match explanations");
        Scored scored = freshOrRescore(user, candidate);
        CompatibilityResult result = scored.result();
        if (result.getExplanation().isEmpty()) {
            result.attachExplanation(generator.generate(user, result));
        }
        return new MatchExplanation(result, result.getExplanation().orElseThrow(), scored.rescored());
    }

    /** F06. Likely friction points and house rules for this pair. Same freshness rule; kept on the result. */
    public FrictionMapView getFrictionMap(User user, User candidate) {
        FrictionMapGenerator generator = configured(frictionMapGenerator, "Friction maps");
        Scored scored = freshOrRescore(user, candidate);
        CompatibilityResult result = scored.result();
        if (result.getFrictionMap().isEmpty()) {
            FrictionMap map = generator.generate(user, result);
            result.attachFrictionMap(map);
        }
        return new FrictionMapView(result, result.getFrictionMap().orElseThrow(), scored.rescored());
    }

    /**
     * F07. Answers a free-form question about one candidate from their stored data. Questions are
     * not cached (each is different); the pair's result is checked for freshness like the others.
     */
    public MatchAnswer answerMatchQuestion(User user, User candidate, String question) {
        MatchQuestionAnswerer answerer = configured(questionAnswerer, "Match questions");
        Scored scored = freshOrRescore(user, candidate);
        MatchQuestionAnswerer.Answer answer = answerer.answer(scored.result(), question);
        return new MatchAnswer(scored.result(), answer.text(), answer.answerable(), scored.rescored());
    }

    // ---- F09: shortlist ----

    public AddResult bookmark(User user, User candidate) {
        return configured(shortlistService, "Shortlists").add(user, candidate);
    }

    /** @return true if the candidate was on the shortlist */
    public boolean removeBookmark(User user, String candidateId) {
        return configured(shortlistService, "Shortlists").remove(user, candidateId);
    }

    public List<ShortlistService.Entry> getShortlist(User user) {
        return configured(shortlistService, "Shortlists").list(user);
    }

    // ---- F01: profile elicitation. One conversation per user; a new one replaces any unfinished one. ----

    /** Starts (or restarts) the elicitation conversation and returns the first question. */
    public String runElicitation(User user) {
        ElicitationAgent fresh = configured(elicitationFactory, "Profile elicitation").get();
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
}
