package com.roommatch.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.ElicitationAgent;
import com.roommatch.agent.ExplanationGenerator;
import com.roommatch.agent.FrictionMapGenerator;
import com.roommatch.agent.MatchQuestionAnswerer;
import com.roommatch.agent.LLMCompatibilityScorer;
import com.roommatch.agent.ProfileAccessTool;
import com.roommatch.agent.ToolManager;
import com.roommatch.config.ConfigException;
import com.roommatch.controller.AgentController;
import com.roommatch.llm.LLMClient;
import com.roommatch.llm.LLMException;
import com.roommatch.platform.HousingPlatformClient;
import com.roommatch.repository.CandidateRepository;
import com.roommatch.repository.PlatformProfileAdapter;
import com.roommatch.service.RankingService;
import com.roommatch.service.ShortlistService;
import com.roommatch.service.ShortlistStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Drives the real shell, controller, agents, adapter and sample data with scripted keyboard input.
 * Only the language model is faked: profile-building prompts get queued replies, scoring prompts
 * get descending scores. No network, no cost.
 */
class InteractiveShellTest {
    private static final String ALL_FOUR = "{\"dimensions\": {\"cleanliness\": \"spotless\", \"noiseTolerance\": \"quiet\","
            + " \"guestFrequency\": \"rarely\", \"sleepSchedule\": \"early riser\"}, \"followUp\": \"\"}";

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final Deque<String> elicitationReplies = new ArrayDeque<>();
    private final AtomicInteger scoringCalls = new AtomicInteger();
    private final AtomicInteger explanationCalls = new AtomicInteger();
    private boolean explanationsFail;
    private final AtomicInteger frictionCalls = new AtomicInteger();
    private final AtomicInteger questionCalls = new AtomicInteger();
    private boolean questionsFail;
    private final Map<String, List<String>> shortlistData = new HashMap<>();
    private boolean frictionFails;

    private String out() { return outBytes.toString(StandardCharsets.UTF_8); }
    private String err() { return errBytes.toString(StandardCharsets.UTF_8); }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    private LLMClient fakeModel() {
        return prompt -> {
            if (prompt.contains("\"answerable\"")) {
                if (questionsFail) {
                    throw new LLMException("service down");
                }
                int n = questionCalls.incrementAndGet();
                return prompt.contains("pets")
                        ? "{\"answerable\": false, \"answer\": \"Their profile says nothing about pets.\"}"
                        : "{\"answerable\": true, \"answer\": \"Answer number " + n + " from their profile.\"}";
            }
            if (prompt.contains("\"frictionPoints\"")) {
                if (frictionFails) {
                    throw new LLMException("service down");
                }
                int n = frictionCalls.incrementAndGet();
                return "{\"frictionPoints\": [\"Friction point " + n + " about tidiness.\", \"Second point about noise levels in the evening.\"],"
                        + " \"suggestedRules\": [\"Rule " + n + ": quiet hours after ten.\"]}";
            }
            if (prompt.contains("Reply with the explanation text only.")) {
                if (explanationsFail) {
                    throw new LLMException("service down");
                }
                int n = explanationCalls.incrementAndGet();
                return "Explanation number " + n + ". They keep a messier home than you do, which affects you more than them.";
            }
            if (prompt.contains("<answer>")) {
                String next = elicitationReplies.poll();
                if (next == null) {
                    throw new AssertionError("unexpected profile-building call");
                }
                if (next.equals("FAIL")) {
                    throw new LLMException("service down");
                }
                return next;
            }
            int n = scoringCalls.incrementAndGet();
            double high = Math.max(0, 100 - 5 * n);
            return "{\"scoreAtoB\": " + high + ", \"scoreBtoA\": " + high + ", \"rationale\": \"r\"}";
        };
    }

    private RoomMatchCLI.ServiceFactory wiring() {
        return () -> {
            LLMClient llm = fakeModel();
            CandidateRepository repo = new PlatformProfileAdapter(
                    HousingPlatformClient.fromJsonFile(Path.of("data", "platform_users.json")));
            ToolManager tools = new ToolManager();
            tools.register(new ProfileAccessTool(repo));
            CompatibilityAgent agent = new CompatibilityAgent(tools, new LLMCompatibilityScorer(llm));
            ShortlistStore store = new ShortlistStore() {
                @Override public List<String> load(String userId) { return shortlistData.getOrDefault(userId, List.of()); }
                @Override public void save(String userId, List<String> ids) { shortlistData.put(userId, new ArrayList<>(ids)); }
            };
            AgentController controller = AgentController.builder(agent, new RankingService())
                    .elicitation(() -> new ElicitationAgent(llm))
                    .explanations(new ExplanationGenerator(llm))
                    .frictionMaps(new FrictionMapGenerator(llm))
                    .questions(new MatchQuestionAnswerer(llm))
                    .shortlists(new ShortlistService(repo, store))
                    .build();
            return new RoomMatchCLI.Services(controller, repo);
        };
    }

    private int shell(RoomMatchCLI.ServiceFactory factory, String... lines) {
        byte[] input = (String.join("\n", lines) + (lines.length > 0 ? "\n" : "")).getBytes(StandardCharsets.UTF_8);
        return new RoomMatchCLI(new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8),
                new ByteArrayInputStream(input), factory).run(new String[] {"shell"});
    }

    private int shell(String... lines) {
        return shell(wiring(), lines);
    }

    // ------------------------------------------------------------------ basics

    @Test
    void startsAndQuitsCleanly() {
        assertEquals(0, shell("quit"));
        assertTrue(out().contains("interactive mode"));
        assertTrue(out().contains("Goodbye."));
    }

    @Test
    void endOfInputAlsoExitsCleanly() {
        assertEquals(0, shell());
        assertTrue(out().contains("Goodbye."));
    }

    @Test
    void helpAndUnknownCommands() {
        shell("help", "dance", "quit");
        assertTrue(out().contains("elicit") && out().contains("edit key=value"));
        assertTrue(err().contains("Unknown command: dance"));
    }

    @Test
    void commandsThatNeedAUserSayToLogInFirst() {
        shell("whoami", "match", "elicit", "edit budget=1", "results", "explain 1", "friction 1", "ask 1 hello", "shortlist", "quit");
        assertEquals(9, count(err(), "Log in first"));
    }

    @Test
    void shellNeedsWorkingSetup() {
        int code = shell(() -> { throw new ConfigException("LLM_API_KEY is not set."); }, "quit");
        assertEquals(1, code);
        assertTrue(err().contains("Setup problem: LLM_API_KEY is not set."));
    }

    // ------------------------------------------------------------------- login

    @Test
    void loginShowsDetailsAndLifestyleProfile() {
        shell("login p001", "quit");
        assertTrue(out().contains("Logged in as p001."));
        assertTrue(out().contains("Lifestyle profile: complete"));
        assertTrue(out().contains("cleanliness: very tidy, cleans daily"));
    }

    @Test
    void loginRejectsUnknownAndDeactivatedAccounts() {
        shell("login nobody", "login p005", "login", "quit");
        assertTrue(err().contains("no user with id 'nobody'"));
        assertTrue(err().contains("deactivated"));
        assertTrue(err().contains("Usage: login <userId>"));
    }

    @Test
    void switchingUsersForgetsTheOldRanking() {
        shell("login p001", "match", "login p002", "results", "quit");
        assertTrue(out().contains("No results yet"));
    }

    // ------------------------------------------------------------------- match

    @Test
    void matchWorksInsideTheShellWithOptions() {
        shell("login p001", "match --top 2", "quit");
        assertTrue(out().contains("Matches for p001"));
        assertTrue(out().contains("Showing the top 2 of 5."));
        assertEquals(5, scoringCalls.get());
    }

    @Test
    void asOptionIsRejectedInsideTheShell() {
        shell("login p001", "match --as p002", "quit");
        assertTrue(err().contains("use 'login <userId>' instead"));
        assertEquals(0, scoringCalls.get());
    }

    @Test
    void incompleteProfileBlocksMatchAndPointsToElicit() {
        shell("login p003", "match", "quit");
        assertTrue(err().contains("incomplete") && err().contains("Type 'elicit'"));
    }

    // -------------------------------------------------- F02 edit + F10 re-rank

    @Test
    void editChangesTheProfile() {
        shell("login p001", "edit budget=1500 location=New York", "whoami", "quit");
        assertTrue(out().contains("Profile updated."));
        assertTrue(out().contains("New York, budget 1500"));
    }

    @Test
    void invalidEditIsRejectedAndNothingChanges() {
        shell("login p001", "edit budget=-5", "edit colour=red", "edit", "edit nonsense", "whoami", "quit");
        assertTrue(err().contains("Budget must be a non-negative number"));
        assertTrue(err().contains("Unknown field: colour"));
        assertTrue(err().contains("Usage: edit"));
        assertTrue(err().contains("Expected key=value"));
        assertTrue(out().contains("budget 1100"));
        assertFalse(out().contains("Profile updated."));
    }

    @Test
    void editBeforeAnyRankingDoesNotAskToReRank() {
        shell("login p001", "edit budget=1500", "quit");
        assertFalse(out().contains("Re-run matching"));
    }

    @Test
    void decliningTheReRankLeavesResultsVisiblyOutOfDate() {
        shell("login p001", "match", "edit budget=1500", "n", "results", "quit");
        assertTrue(out().contains("out of date. Re-run matching now? [y/n]"));
        assertTrue(out().contains("stay marked as out of date"));
        assertTrue(out().contains("WARNING: these results are OUT OF DATE"));
        assertEquals(5, scoringCalls.get(), "declining must not call the model");
    }

    @Test
    void acceptingTheReRankRunsMatchingAgainAndClearsTheWarning() {
        shell("login p001", "match", "edit budget=1500", "y", "results", "quit");
        assertEquals(10, scoringCalls.get());
        assertEquals(3, count(out(), "Matches for p001"), "match, automatic re-rank, results");
        assertFalse(out().contains("WARNING"));
    }

    @Test
    void runningMatchYourselfAlsoClearsTheWarning() {
        shell("login p001", "match", "edit budget=1500", "n", "match", "results", "quit");
        assertFalse(out().contains("WARNING"));
    }

    // ------------------------------------------------------ F01 conversation

    @Test
    void elicitBuildsAProfileThatMakesRankingPossible() {
        elicitationReplies.add(ALL_FOUR);
        shell("login p006", "elicit", "I am very tidy, quiet, rarely host, and sleep early", "/confirm", "whoami", "match", "quit");
        assertTrue(out().contains("Lifestyle profile: not set"));
        assertTrue(out().contains("That is everything I need"));
        assertTrue(out().contains("Profile saved."));
        assertTrue(out().contains("Lifestyle profile: complete"));
        assertTrue(out().contains("Matches for p006"));
        assertTrue(scoringCalls.get() > 0);
    }

    @Test
    void aVagueAnswerShowsTheAgentsFollowUp() {
        elicitationReplies.add("{\"dimensions\": {}, \"followUp\": \"Tidy in what way?\"}");
        shell("login p006", "elicit", "it depends", "/cancel", "quit");
        assertTrue(out().contains("Agent: Tidy in what way?"));
    }

    @Test
    void stopSavesPartialProgressButRankingStaysBlocked() {
        elicitationReplies.add("{\"dimensions\": {\"cleanliness\": \"tidy\"}, \"followUp\": \"\"}");
        shell("login p006", "elicit", "I am tidy", "/stop", "match", "quit");
        assertTrue(out().contains("Progress saved. Still missing: noiseTolerance, guestFrequency, sleepSchedule"));
        assertTrue(err().contains("incomplete"));
        assertEquals(0, scoringCalls.get());
    }

    @Test
    void cancelDiscardsTheConversation() {
        elicitationReplies.add(ALL_FOUR);
        shell("login p006", "elicit", "everything", "/cancel", "whoami", "quit");
        assertTrue(out().contains("Discarded."));
        assertEquals(2, count(out(), "Lifestyle profile: not set"), "login and whoami both show no profile");
    }

    @Test
    void correctLetsThePersonFillInAValueThemselves() {
        elicitationReplies.add("{\"dimensions\": {\"noiseTolerance\": \"quiet\", \"guestFrequency\": \"rarely\","
                + " \"sleepSchedule\": \"early\"}, \"followUp\": \"Tidy in what way?\"}");
        shell("login p006", "elicit", "quiet, few guests, early", "/correct cleanliness I keep it spotless",
                "/show", "/confirm", "quit");
        assertTrue(out().contains("Agent: Tidy in what way?"));
        assertTrue(out().contains("Updated cleanliness."));
        assertTrue(out().contains("Everything is filled in. Type /confirm to save."));
        assertTrue(out().contains("cleanliness: I keep it spotless"));
        assertTrue(out().contains("Profile saved."));
    }

    @Test
    void aModelFailureDuringTheConversationLosesNothing() {
        elicitationReplies.add("FAIL");
        elicitationReplies.add(ALL_FOUR);
        shell("login p006", "elicit", "my answer", "my answer", "/confirm", "quit");
        assertTrue(err().contains("The AI service failed") && err().contains("you can try again"));
        assertTrue(out().contains("That is everything I need"));
        assertTrue(out().contains("Profile saved."));
    }

    @Test
    void badProfileCommandsAreExplained() {
        elicitationReplies.add(ALL_FOUR);
        shell("login p006", "elicit", "/wat", "/correct cleanliness", "/correct pets dog", "/cancel", "quit");
        assertTrue(err().contains("Unknown profile command '/wat'"));
        assertTrue(err().contains("Usage: /correct"));
        assertTrue(err().contains("Unknown lifestyle dimension: pets"));
    }

    @Test
    void savingANewProfileAfterRankingOffersAReRank() {
        elicitationReplies.add(ALL_FOUR);
        shell("login p001", "match", "elicit", "my answers", "/confirm", "n", "results", "quit");
        assertTrue(out().contains("Re-run matching now? [y/n]"));
        assertTrue(out().contains("WARNING: these results are OUT OF DATE"));
    }

    // ----------------------------------------------------------- F05 explain

    @Test
    void explainWorksByRankNumberAndByCandidateId() {
        shell("login p001", "match", "explain 1", "explain p003", "quit");
        assertTrue(out().contains("Why p002 matched:"), "rank 1 is p002 with the fake scores");
        assertTrue(out().contains("Why p003 matched:"));
        assertTrue(out().contains("Explanation number 1.") && out().contains("Explanation number 2."));
        assertTrue(out().contains("You->Them") && out().contains("Overall"));
        assertEquals(5, scoringCalls.get(), "fresh results are explained without re-scoring");
        assertEquals(2, explanationCalls.get());
    }

    @Test
    void askingForTheSameExplanationAgainCostsNoModelCall() {
        shell("login p001", "match", "explain 1", "explain 1", "quit");
        assertEquals(2, count(out(), "Why p002 matched:"));
        assertEquals(2, count(out(), "Explanation number 1."));
        assertEquals(1, explanationCalls.get());
    }

    @Test
    void explainShowsWhichDimensionsWereNotCompared() {
        shell("login p001", "match", "explain p003", "quit");
        assertTrue(out().contains("Not compared (missing data): guestFrequency, sleepSchedule"));
    }

    @Test
    void explainBeforeAnyRankingTellsYouToMatchFirst() {
        shell("login p001", "explain 1", "quit");
        assertTrue(out().contains("No results yet. Type 'match' first."));
        assertEquals(0, explanationCalls.get());
    }

    @Test
    void explainRejectsBadReferences() {
        shell("login p001", "match", "explain 99", "explain 0", "explain p999", "explain p006", "explain", "quit");
        assertTrue(err().contains("There is no match number 99 (your last ranking has 5)."));
        assertTrue(err().contains("There is no match number 0"));
        assertTrue(err().contains("'p999' is not in your last ranking."));
        assertTrue(err().contains("'p006' is not in your last ranking."), "skipped candidates have no result to explain");
        assertTrue(err().contains("Usage: explain"));
        assertEquals(0, explanationCalls.get());
    }

    @Test
    void afterAProfileEditExplainRescoresThatMatchFirstAndSaysSo() {
        shell("login p001", "match", "edit budget=1500", "n", "explain 1", "quit");
        assertTrue(out().contains("re-scored first"));
        assertEquals(6, scoringCalls.get(), "five for the ranking, one for the re-scored pair");
        assertEquals(1, explanationCalls.get());
    }

    @Test
    void aModelFailureWhileExplainingIsReportedAndNothingIsCached() {
        explanationsFail = true;
        shell("login p001", "match", "explain 1", "quit");
        assertTrue(err().contains("The AI service failed"));
        assertFalse(out().contains("Why p002 matched:"));
    }

    // ---------------------------------------------------------- F06 friction

    @Test
    void frictionPrintsNumberedPointsAndRulesForAMatch() {
        shell("login p001", "match", "friction 1", "quit");
        String o = out();
        assertTrue(o.contains("Friction map for p002:"));
        assertTrue(o.contains("Likely friction points:") && o.contains("House rules to discuss:"));
        assertTrue(o.contains("  1. Friction point 1 about tidiness."));
        assertTrue(o.contains("  2. Second point about noise levels in the evening."));
        assertTrue(o.contains("  1. Rule 1: quiet hours after ten."));
        assertTrue(o.contains("You->Them") && o.contains("Overall"));
        assertEquals(5, scoringCalls.get(), "no re-scoring for a fresh result");
        assertEquals(1, frictionCalls.get());
    }

    @Test
    void frictionAlsoWorksByCandidateIdAndPartialMapsSaySo() {
        shell("login p001", "match", "friction p003", "quit");
        assertTrue(out().contains("Friction map for p003:"));
        assertTrue(out().contains("Partial map. Not assessed (missing data): guestFrequency, sleepSchedule"));
    }

    @Test
    void revisitingAMapCostsNoModelCall() {
        shell("login p001", "match", "friction 1", "friction 1", "quit");
        assertEquals(2, count(out(), "Friction map for p002:"));
        assertEquals(2, count(out(), "Friction point 1 about tidiness."));
        assertEquals(1, frictionCalls.get());
    }

    @Test
    void frictionBeforeRankingAndBadReferencesAreExplained() {
        shell("login p001", "friction 1", "match", "friction 99", "friction p999", "friction", "quit");
        assertTrue(out().contains("No results yet. Type 'match' first."));
        assertTrue(err().contains("There is no match number 99"));
        assertTrue(err().contains("'p999' is not in your last ranking."));
        assertTrue(err().contains("Usage: friction"));
        assertEquals(0, frictionCalls.get());
    }

    @Test
    void afterAProfileEditFrictionRescoresFirstAndBuildsANewMap() {
        shell("login p001", "match", "friction 1", "edit budget=1500", "n", "friction 1", "quit");
        assertTrue(out().contains("re-scored first"));
        assertEquals(6, scoringCalls.get());
        assertEquals(2, frictionCalls.get());
        assertTrue(out().contains("Friction point 2 about tidiness."));
    }

    @Test
    void explainAndFrictionAfterAnEditShareASingleRescore() {
        shell("login p001", "match", "edit budget=1500", "n", "explain 1", "friction 1", "quit");
        assertEquals(6, scoringCalls.get(), "five for the ranking, one shared re-score");
        assertEquals(1, count(out(), "re-scored first"));
    }

    @Test
    void aModelFailureWhileBuildingTheMapIsReportedAndNothingIsCached() {
        frictionFails = true;
        shell("login p001", "match", "friction 1", "quit");
        assertTrue(err().contains("The AI service failed"));
        assertFalse(out().contains("Friction map for"));
    }

    // ------------------------------------------------------------- F07 ask

    @Test
    void askAnswersFromTheCandidatesDataByRankOrId() {
        shell("login p001", "match", "ask 1 How tidy are they?", "ask p003 Do they host guests?", "quit");
        assertTrue(out().contains("About p002:") && out().contains("Answer number 1 from their profile."));
        assertTrue(out().contains("About p003:") && out().contains("Answer number 2 from their profile."));
        assertEquals(5, scoringCalls.get(), "no re-scoring for fresh results");
    }

    @Test
    void askSaysWhenTheProfileDoesNotCoverTheQuestion() {
        shell("login p001", "match", "ask 1 Do they have pets?", "quit");
        assertTrue(out().contains("Their profile says nothing about pets."));
        assertTrue(out().contains("(Their profile data does not cover this question.)"));
    }

    @Test
    void askDoesNotCacheAnswersBecauseEveryQuestionIsDifferent() {
        shell("login p001", "match", "ask 1 same question?", "ask 1 same question?", "quit");
        assertEquals(2, questionCalls.get());
    }

    @Test
    void askExplainsBadUsage() {
        shell("login p001", "ask 1 hello", "match", "ask", "ask 1", "ask 99 hi", "ask p999 hi", "quit");
        assertTrue(out().contains("No results yet. Type 'match' first."));
        assertEquals(2, count(err(), "Usage: ask"));
        assertTrue(err().contains("There is no match number 99"));
        assertTrue(err().contains("'p999' is not in your last ranking."));
        assertEquals(0, questionCalls.get());
    }

    @Test
    void askAfterAProfileEditRescoresFirstAndSaysSo() {
        shell("login p001", "match", "edit budget=1500", "n", "ask 1 anything?", "quit");
        assertTrue(out().contains("re-scored first"));
        assertEquals(6, scoringCalls.get());
    }

    @Test
    void aModelFailureWhileAnsweringIsReported() {
        questionsFail = true;
        shell("login p001", "match", "ask 1 anything?", "quit");
        assertTrue(err().contains("The AI service failed"));
        assertFalse(out().contains("About p002"));
    }

    // ------------------------------------------------------- F09 shortlist

    @Test
    void shortlistAddListAndRemoveWork() {
        shell("login p001", "match", "shortlist add 1", "shortlist add p003", "shortlist", "shortlist remove 1",
                "shortlist remove p003", "shortlist remove p003", "shortlist", "quit");
        assertTrue(out().contains("Added p002 to your shortlist."));
        assertTrue(out().contains("Added p003 to your shortlist."));
        assertTrue(out().contains("Your shortlist (2):"));
        assertTrue(out().contains("1. p002  Toronto  rent 1500  available 2027-01-01"));
        assertTrue(out().contains("Removed p002 from your shortlist."));
        assertTrue(out().contains("Removed p003 from your shortlist."));
        assertTrue(out().contains("p003 is not in your shortlist."));
        assertTrue(out().contains("Your shortlist is empty."));
    }

    @Test
    void addingTheSameMatchTwiceIsReported() {
        shell("login p001", "match", "shortlist add 1", "shortlist add 1", "quit");
        assertTrue(out().contains("p002 is already in your shortlist."));
    }

    @Test
    void theShortlistSurvivesAcrossRunsAndLogins() {
        shell("login p001", "match", "shortlist add 1", "quit");
        outBytes.reset();
        errBytes.reset();
        shell("login p001", "shortlist", "login p002", "shortlist", "login p001", "shortlist", "quit");
        assertEquals(2, count(out(), "Your shortlist (1):"), "p001 sees it each time");
        assertEquals(1, count(out(), "Your shortlist is empty."), "p002 has their own, empty, shortlist");
    }

    @Test
    void aBookmarkedPersonWhoIsGoneShowsAsNoLongerAvailable() {
        shortlistData.put("p001", new ArrayList<>(List.of("p002", "p005", "ghost")));
        shell("login p001", "shortlist", "quit");
        assertTrue(out().contains("Your shortlist (3):"));
        assertTrue(out().contains("1. p002  Toronto"));
        assertTrue(out().contains("2. p005  no longer available"));
        assertTrue(out().contains("3. ghost  no longer available"));
    }

    @Test
    void shortlistExplainsBadUsage() {
        shell("login p001", "shortlist add 1", "match", "shortlist add", "shortlist add 99", "shortlist remove",
                "shortlist remove 5", "shortlist frobnicate", "quit");
        assertTrue(out().contains("No results yet. Type 'match' first."));
        assertTrue(err().contains("Usage: shortlist add"));
        assertTrue(err().contains("There is no match number 99"));
        assertTrue(err().contains("Usage: shortlist remove"));
        assertTrue(err().contains("There is no shortlist entry 5 (you have 0)."));
        assertTrue(err().contains("Unknown shortlist command 'frobnicate'"));
    }
}
