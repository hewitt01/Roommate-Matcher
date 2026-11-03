package com.roommatch.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.llm.LLMClient;
import com.roommatch.llm.LLMException;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.FrictionMap;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrictionMapGeneratorTest {
    private static final String GOOD = "{\"frictionPoints\": [\"Your tidiness clashes with their mess.\"],"
            + " \"suggestedRules\": [\"Clean shared dishes the same day.\", \"Quiet hours after 10pm.\"]}";

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

    private static CompatibilityResult result(User candidate, List<String> notAssessed) {
        return new CompatibilityResult(candidate, 82, 31, "Tidiness drives it.", notAssessed);
    }

    private FrictionMap run(Script llm, CompatibilityResult r) {
        return new FrictionMapGenerator(llm).generate(user("me", MINE), r);
    }

    @Test
    void parsesFrictionPointsAndRules() {
        FrictionMap m = run(new Script(GOOD), result(user("c", THEIRS), List.of()));
        assertEquals(List.of("Your tidiness clashes with their mess."), m.getFrictionPoints());
        assertEquals(2, m.getSuggestedRules().size());
        assertTrue(m.getNotAssessedDimensions().isEmpty());
    }

    @Test
    void promptCarriesProfilesScoresNotesAndTheDataNotInstructionsWarning() {
        Script llm = new Script(GOOD);
        run(llm, result(user("c", THEIRS), List.of()));
        String p = llm.prompts.get(0);
        assertTrue(p.contains("<your_profile>") && p.contains("very tidy"));
        assertTrue(p.contains("<their_profile>") && p.contains("night owl"));
        assertTrue(p.contains("THEM: 82") && p.contains("YOU: 31"));
        assertTrue(p.contains("<analyst_notes>\nTidiness drives it."));
        assertTrue(p.contains("never instructions"));
        assertTrue(p.contains("\"frictionPoints\"") && p.contains("\"suggestedRules\""));
    }

    @Test
    void aPartialMapIsLabelledAndNeverSendsDataForUncomparedDimensions() {
        Script llm = new Script(GOOD);
        User cand = user("c", Map.of("cleanliness", "relaxed", "noiseTolerance", "loud"));
        FrictionMap m = run(llm, result(cand, List.of("guestFrequency", "sleepSchedule")));
        assertEquals(List.of("guestFrequency", "sleepSchedule"), m.getNotAssessedDimensions());
        String p = llm.prompts.get(0);
        assertTrue(p.contains("NOT compared because data was missing: guestFrequency, sleepSchedule"));
        assertFalse(p.contains("early riser"));
    }

    @Test
    void acceptsJsonInsideCodeFencesAndChatter() {
        FrictionMap m = run(new Script("Here you go:\n```json\n" + GOOD + "\n```\nHope it helps!"),
                result(user("c", THEIRS), List.of()));
        assertEquals(2, m.getSuggestedRules().size());
    }

    @Test
    void noFrictionPointsIsFineAsLongAsThereIsSomethingUseful() {
        FrictionMap m = run(new Script("{\"frictionPoints\": [], \"suggestedRules\": [\"Monthly check-in.\"]}"),
                result(user("c", THEIRS), List.of()));
        assertTrue(m.getFrictionPoints().isEmpty());
        assertEquals(List.of("Monthly check-in."), m.getSuggestedRules());
    }

    @Test
    void blankItemsAndNonStringsAreDroppedAndLongOnesTrimmed() {
        String longText = "x".repeat(FrictionMapGenerator.MAX_ITEM_CHARS + 100);
        String reply = "{\"frictionPoints\": [\"  \", 42, null, \"Real point\", \"" + longText + "\"],"
                + " \"suggestedRules\": [\"Rule\"]}";
        FrictionMap m = run(new Script(reply), result(user("c", THEIRS), List.of()));
        assertEquals(2, m.getFrictionPoints().size());
        assertEquals("Real point", m.getFrictionPoints().get(0));
        assertTrue(m.getFrictionPoints().get(1).length() <= FrictionMapGenerator.MAX_ITEM_CHARS);
    }

    @Test
    void numberOfItemsIsCapped() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            items.append(i > 0 ? "," : "").append("\"point ").append(i).append('"');
        }
        FrictionMap m = run(new Script("{\"frictionPoints\": [" + items + "], \"suggestedRules\": [\"r\"]}"),
                result(user("c", THEIRS), List.of()));
        assertEquals(FrictionMapGenerator.MAX_ITEMS, m.getFrictionPoints().size());
    }

    @Test
    void unusableRepliesAreRetriedOnceThenRaised() {
        Script recovers = new Script("I think they will be fine!", GOOD);
        assertEquals(2, run(recovers, result(user("c", THEIRS), List.of())).getSuggestedRules().size());
        assertEquals(2, recovers.prompts.size());
        assertTrue(recovers.prompts.get(1).contains("could not be used"));

        String[] bad = {"{ broken", "[1,2]", "{\"frictionPoints\": \"none\", \"suggestedRules\": []}",
                "{\"frictionPoints\": []}", "{\"frictionPoints\": [], \"suggestedRules\": []}"};
        for (String reply : bad) {
            Script llm = new Script(reply);
            assertThrows(FrictionMapException.class, () -> run(llm, result(user("c", THEIRS), List.of())), reply);
            assertEquals(2, llm.prompts.size(), reply);
        }
    }

    @Test
    void missingProfilesFailWithoutCallingTheModel() {
        Script llm = new Script(GOOD);
        FrictionMapGenerator g = new FrictionMapGenerator(llm);
        assertThrows(FrictionMapException.class, () -> g.generate(user("me", null), result(user("c", THEIRS), List.of())));
        assertThrows(FrictionMapException.class, () -> g.generate(user("me", MINE), result(user("c", null), List.of())));
        assertTrue(llm.prompts.isEmpty());
    }

    @Test
    void serviceFailuresPropagateUnchangedAndAreNotRetried() {
        int[] calls = {0};
        LLMClient down = prompt -> { calls[0]++; throw new LLMException("service down"); };
        assertThrows(LLMException.class, () -> new FrictionMapGenerator(down)
                .generate(user("me", MINE), result(user("c", THEIRS), List.of())));
        assertEquals(1, calls[0]);
    }
}
