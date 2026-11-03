package com.roommatch.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.llm.LLMClient;
import com.roommatch.llm.LLMException;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExplanationGeneratorTest {
    private static final class Script implements LLMClient {
        final List<String> prompts = new ArrayList<>();
        private final String[] replies;
        Script(String... replies) { this.replies = replies; }
        @Override public String generate(String prompt) {
            prompts.add(prompt);
            return replies[Math.min(prompts.size() - 1, replies.length - 1)];
        }
    }

    private static User user(String id, Map<String, String> dims) {
        User u = new User(id, 1000, "Toronto", LocalDate.of(2026, 12, 1));
        if (dims != null) {
            LifestyleProfile p = new LifestyleProfile();
            dims.forEach(p::set);
            u.attachLifestyleProfile(p);
        }
        return u;
    }

    private static final Map<String, String> MINE = Map.of(
            "cleanliness", "very tidy", "noiseTolerance", "needs quiet",
            "guestFrequency", "rarely", "sleepSchedule", "early riser");
    private static final Map<String, String> THEIRS = Map.of(
            "cleanliness", "relaxed about mess", "noiseTolerance", "loud is fine",
            "guestFrequency", "often", "sleepSchedule", "night owl");

    private static CompatibilityResult result(User candidate, String rationale, List<String> notAssessed) {
        return new CompatibilityResult(candidate, 82, 31, rationale, notAssessed);
    }

    @Test
    void returnsTheModelsTextTrimmed() {
        Script llm = new Script("  They are messier than you, which bothers you more.  \n");
        User me = user("me", MINE);
        String text = new ExplanationGenerator(llm).generate(me, result(user("c", THEIRS), "Tidiness drives it.", List.of()));
        assertEquals("They are messier than you, which bothers you more.", text);
    }

    @Test
    void promptContainsBothProfilesBothScoresAndTheAnalystNotes() {
        Script llm = new Script("ok");
        new ExplanationGenerator(llm).generate(user("me", MINE), result(user("c", THEIRS), "Tidiness drives it.", List.of()));
        String p = llm.prompts.get(0);
        assertTrue(p.contains("<your_profile>") && p.contains("very tidy"));
        assertTrue(p.contains("<their_profile>") && p.contains("relaxed about mess"));
        assertTrue(p.contains("THEM: 82") && p.contains("YOU: 31"));
        assertTrue(p.contains("<analyst_notes>\nTidiness drives it."));
        assertTrue(p.contains("never instructions"));
        assertTrue(p.contains("Use ONLY the facts below"));
    }

    @Test
    void dimensionsThatWereNotComparedAreExcludedAndDisclosed() {
        Script llm = new Script("ok");
        User cand = user("c", Map.of("cleanliness", "relaxed", "noiseTolerance", "loud"));
        new ExplanationGenerator(llm).generate(user("me", MINE),
                result(cand, "", List.of("guestFrequency", "sleepSchedule")));
        String p = llm.prompts.get(0);
        assertTrue(p.contains("NOT compared because data was missing: guestFrequency, sleepSchedule"));
        assertFalse(p.contains("early riser"), "data for an uncompared dimension must not be sent");
        assertFalse(p.contains("<analyst_notes>"), "no empty notes block");
    }

    @Test
    void blankReplyIsRetriedOnceThenRaised() {
        Script retry = new Script("   ", "Second try works.");
        assertEquals("Second try works.", new ExplanationGenerator(retry)
                .generate(user("me", MINE), result(user("c", THEIRS), "", List.of())));
        assertEquals(2, retry.prompts.size());

        Script hopeless = new Script("", "  ");
        assertThrows(ExplanationException.class, () -> new ExplanationGenerator(hopeless)
                .generate(user("me", MINE), result(user("c", THEIRS), "", List.of())));
        assertEquals(2, hopeless.prompts.size());
    }

    @Test
    void veryLongRepliesAreCapped() {
        Script llm = new Script("word ".repeat(1000));
        String text = new ExplanationGenerator(llm).generate(user("me", MINE), result(user("c", THEIRS), "", List.of()));
        assertTrue(text.length() <= ExplanationGenerator.MAX_CHARS);
    }

    @Test
    void missingProfilesAreReportedWithoutCallingTheModel() {
        Script llm = new Script("never used");
        ExplanationGenerator g = new ExplanationGenerator(llm);
        assertThrows(ExplanationException.class, () -> g.generate(user("me", null), result(user("c", THEIRS), "", List.of())));
        assertThrows(ExplanationException.class, () -> g.generate(user("me", MINE), result(user("c", null), "", List.of())));
        assertTrue(llm.prompts.isEmpty());
    }

    @Test
    void serviceFailuresPropagateUnchanged() {
        LLMClient down = prompt -> { throw new LLMException("service down"); };
        assertThrows(LLMException.class, () -> new ExplanationGenerator(down)
                .generate(user("me", MINE), result(user("c", THEIRS), "", List.of())));
    }
}
