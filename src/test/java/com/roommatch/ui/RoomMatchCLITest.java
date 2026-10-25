package com.roommatch.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.agent.CompatibilityAgent;
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
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * End-to-end through the real CLI, controller, agent, scorer, adapter and the committed sample
 * data. Only the language model is replaced by a scripted fake, so no network and no cost.
 */
class RoomMatchCLITest {
    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final AtomicInteger llmCalls = new AtomicInteger();

    private String out() { return outBytes.toString(StandardCharsets.UTF_8); }
    private String err() { return errBytes.toString(StandardCharsets.UTF_8); }

    /** Each call gets a lower score than the last, so ranking order equals retrieval order. */
    private LLMClient descendingScores() {
        return prompt -> {
            int n = llmCalls.incrementAndGet();
            double high = 100 - 10 * n;
            return "{\"scoreAtoB\": " + high + ", \"scoreBtoA\": " + (high + 5)
                    + ", \"rationale\": \"Reason " + n + ".\"}";
        };
    }

    private RoomMatchCLI.ServiceFactory realWiring(LLMClient llm) {
        return () -> {
            CandidateRepository repo = new PlatformProfileAdapter(
                    HousingPlatformClient.fromJsonFile(Path.of("data", "platform_users.json")));
            ToolManager tools = new ToolManager();
            tools.register(new ProfileAccessTool(repo));
            CompatibilityAgent agent = new CompatibilityAgent(tools, new LLMCompatibilityScorer(llm));
            return new RoomMatchCLI.Services(new AgentController(agent, new RankingService()), repo);
        };
    }

    private int run(RoomMatchCLI.ServiceFactory factory, String... args) {
        return new RoomMatchCLI(new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8), factory).run(args);
    }

    private int run(String... args) {
        return run(realWiring(descendingScores()), args);
    }

    @Test
    void matchPrintsARankedTableAndSkipsCandidatesWithNoData() {
        int code = run("match", "--as", "p001");
        assertEquals(0, code, err());
        String o = out();
        assertTrue(o.contains("Matches for p001"));
        assertTrue(o.contains("You->Them") && o.contains("Them->You") && o.contains("Overall"));
        // retrieval order p002, p003, p004, p007, p008 gets descending scores
        assertTrue(o.indexOf("p002") < o.indexOf("p003"));
        assertTrue(o.indexOf("p003") < o.indexOf("p004"));
        assertTrue(o.indexOf("p007") < o.indexOf("p008"));
        assertTrue(o.contains("5 ranked, 1 skipped"));
        assertTrue(o.contains("p006"), "candidate with no habits is listed as skipped");
        assertFalse(o.contains("p005"), "deactivated users never appear");
        assertEquals(5, llmCalls.get(), "one model call per scorable candidate, none for the skipped one");
    }

    @Test
    void partialCandidateIsScoredOnlyOnSharedDimensionsAndFlagged() {
        run("match", "--as", "p001");
        assertTrue(out().contains("not assessed (missing data): guestFrequency, sleepSchedule"));
    }

    @Test
    void filtersNarrowTheRanking() {
        assertEquals(0, run("match", "--as", "p001", "--city", "Vaughan"));
        String o = out();
        assertTrue(o.contains("p003") && o.contains("p007"));
        assertFalse(o.contains("p002") || o.contains("p008"));
    }

    @Test
    void topLimitsRowsAndSaysSo() {
        assertEquals(0, run("match", "--as", "p001", "--top", "2"));
        String o = out();
        assertTrue(o.contains("p002") && o.contains("p003"));
        assertFalse(o.contains("p004"));
        assertTrue(o.contains("Showing the top 2 of 5."));
    }

    @Test
    void rationaleFlagShowsTheModelsReasoning() {
        assertEquals(0, run("match", "--as", "p001", "--rationale"));
        assertTrue(out().contains("Reason 1."));
        outBytes.reset();
        run("match", "--as", "p001");
        assertFalse(out().contains("Reason 1."));
    }

    @Test
    void emptyPoolExplainsHowToRecover() {
        assertEquals(0, run("match", "--as", "p001", "--city", "Ottawa"));
        assertTrue(out().contains("No candidates match your filters"));
        assertEquals(0, llmCalls.get());
    }

    @Test
    void incompleteProfileBlocksRankingWithAClearMessage() {
        assertEquals(1, run("match", "--as", "p003"));   // p003 only has two habits in the sample data
        assertTrue(err().contains("incomplete"));
        assertTrue(err().contains("guestFrequency"));
        assertEquals(0, llmCalls.get());
    }

    @Test
    void unknownOrDeactivatedUsersAreRejected() {
        assertEquals(1, run("match", "--as", "nobody"));
        assertTrue(err().contains("no user with id 'nobody'"));
        errBytes.reset();
        assertEquals(1, run("match", "--as", "p005"));
        assertTrue(err().contains("deactivated"));
    }

    @Test
    void modelServiceFailureIsReportedWithoutAStackTrace() {
        int code = run(realWiring(prompt -> { throw new LLMException("rejected the API key"); }), "match", "--as", "p001");
        assertEquals(1, code);
        assertTrue(err().contains("AI service failed"));
        assertFalse(err().contains("Exception"));
    }

    @Test
    void missingConfigurationIsReportedAsASetupProblem() {
        int code = run(() -> { throw new ConfigException("LLM_API_KEY is not set."); }, "match", "--as", "p001");
        assertEquals(1, code);
        assertTrue(err().contains("Setup problem: LLM_API_KEY is not set."));
    }

    @Test
    void helpNeverNeedsServicesOrAKey() {
        assertEquals(0, run(() -> { throw new AssertionError("must not be built"); }, "help"));
        assertTrue(out().contains("match --as <userId>"));
    }

    @Test
    void badCommandLinesGiveUsageErrors() {
        RoomMatchCLI.ServiceFactory never = () -> { throw new AssertionError("must not be built"); };
        assertEquals(2, run(never));
        assertEquals(2, run(never, "dance"));
        assertEquals(2, run(never, "match"));                                   // missing --as
        assertEquals(2, run(never, "match", "--as"));                           // missing value
        assertEquals(2, run(never, "match", "--as", "p001", "--colour", "red"));
        assertEquals(2, run(never, "match", "--as", "p001", "--max-budget", "lots"));
        assertEquals(2, run(never, "match", "--as", "p001", "--from", "tomorrow"));
        assertEquals(2, run(never, "match", "--as", "p001", "--top", "0"));
        assertEquals(2, run(never, "match", "--as", "p001", "--min-budget", "2000", "--max-budget", "1000"));
        assertFalse(err().isEmpty());
    }
}
