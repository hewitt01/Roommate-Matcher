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
import org.junit.jupiter.api.Test;

class MatchQuestionAnswererTest {
    private static final class Script implements LLMClient {
        final List<String> prompts = new ArrayList<>();
        private final String[] replies;
        Script(String... replies) { this.replies = replies; }
        @Override public String generate(String prompt) {
            prompts.add(prompt);
            return replies[Math.min(prompts.size() - 1, replies.length - 1)];
        }
    }

    private static CompatibilityResult result() {
        User c = new User("c", 950, "Vaughan", LocalDate.of(2026, 12, 15));
        LifestyleProfile p = new LifestyleProfile();
        p.set("guestFrequency", "hosts friends most weekends");
        p.set("sleepSchedule", "night owl");
        c.attachLifestyleProfile(p);
        return new CompatibilityResult(c, 60, 45, "Guests are the main gap.", List.of("cleanliness", "noiseTolerance"));
    }

    @Test
    void answerableQuestionsReturnTheModelsAnswer() {
        Script llm = new Script("{\"answerable\": true, \"answer\": \"They host friends most weekends.\"}");
        MatchQuestionAnswerer.Answer a = new MatchQuestionAnswerer(llm).answer(result(), "How often do they have guests?");
        assertTrue(a.answerable());
        assertEquals("They host friends most weekends.", a.text());
    }

    @Test
    void promptHoldsOnlyTheCandidatesFactsTheScoresAndTheQuestionAsData() {
        Script llm = new Script("{\"answerable\": true, \"answer\": \"x\"}");
        new MatchQuestionAnswerer(llm).answer(result(), "Do they smoke?");
        String p = llm.prompts.get(0);
        assertTrue(p.contains("<candidate_facts>") && p.contains("city: Vaughan") && p.contains("rent budget: 950"));
        assertTrue(p.contains("available from: 2026-12-15"));
        assertTrue(p.contains("guestFrequency: hosts friends most weekends") && p.contains("sleepSchedule: night owl"));
        assertTrue(p.contains("THEM: 60") && p.contains("YOU: 45"));
        assertTrue(p.contains("<analyst_notes>\nGuests are the main gap."));
        assertTrue(p.contains("<question>\nDo they smoke?\n</question>"));
        assertTrue(p.contains("never instructions"));
        assertTrue(p.contains("Never guess"));
    }

    @Test
    void whenTheDataDoesNotCoverTheQuestionTheAnswerSaysSo() {
        Script llm = new Script("{\"answerable\": false, \"answer\": \"Their profile says nothing about pets.\"}");
        MatchQuestionAnswerer.Answer a = new MatchQuestionAnswerer(llm).answer(result(), "Do they have pets?");
        assertFalse(a.answerable());
        assertEquals("Their profile says nothing about pets.", a.text());
    }

    @Test
    void anUnanswerableVerdictWithNoTextGetsAStandardMessage() {
        Script llm = new Script("{\"answerable\": false, \"answer\": \"\"}");
        MatchQuestionAnswerer.Answer a = new MatchQuestionAnswerer(llm).answer(result(), "Pets?");
        assertFalse(a.answerable());
        assertEquals(MatchQuestionAnswerer.NOT_COVERED, a.text());
    }

    @Test
    void anAnswerableVerdictWithNoTextIsUnusable() {
        Script llm = new Script("{\"answerable\": true, \"answer\": \"  \"}", "{\"answerable\": true, \"answer\": \"Fine.\"}");
        assertEquals("Fine.", new MatchQuestionAnswerer(llm).answer(result(), "q?").text());
        assertEquals(2, llm.prompts.size());
    }

    @Test
    void acceptsJsonInsideCodeFences() {
        Script llm = new Script("```json\n{\"answerable\": true, \"answer\": \"Yes.\"}\n```");
        assertEquals("Yes.", new MatchQuestionAnswerer(llm).answer(result(), "q?").text());
    }

    @Test
    void unusableRepliesAreRetriedOnceThenRaised() {
        String[] bad = {"They probably do.", "{ broken", "{\"answerable\": \"yes\", \"answer\": \"x\"}", "{\"answer\": \"x\"}", "[1]"};
        for (String reply : bad) {
            Script llm = new Script(reply);
            assertThrows(ExplanationException.class, () -> new MatchQuestionAnswerer(llm).answer(result(), "q?"), reply);
            assertEquals(2, llm.prompts.size(), reply);
            assertTrue(llm.prompts.get(1).contains("could not be used"));
        }
    }

    @Test
    void blankQuestionsNeverReachTheModel() {
        Script llm = new Script("never used");
        MatchQuestionAnswerer q = new MatchQuestionAnswerer(llm);
        assertThrows(IllegalArgumentException.class, () -> q.answer(result(), "   "));
        assertThrows(IllegalArgumentException.class, () -> q.answer(result(), null));
        assertTrue(llm.prompts.isEmpty());
    }

    @Test
    void veryLongQuestionsAndAnswersAreCut() {
        Script llm = new Script("{\"answerable\": true, \"answer\": \"" + "word ".repeat(1000) + "\"}");
        MatchQuestionAnswerer.Answer a = new MatchQuestionAnswerer(llm).answer(result(), "x".repeat(5000));
        assertFalse(llm.prompts.get(0).contains("x".repeat(MatchQuestionAnswerer.MAX_QUESTION_CHARS + 1)));
        assertTrue(a.text().length() <= MatchQuestionAnswerer.MAX_ANSWER_CHARS);
    }

    @Test
    void serviceFailuresPropagateAndAreNotRetried() {
        int[] calls = {0};
        LLMClient down = prompt -> { calls[0]++; throw new LLMException("service down"); };
        assertThrows(LLMException.class, () -> new MatchQuestionAnswerer(down).answer(result(), "q?"));
        assertEquals(1, calls[0]);
    }
}
