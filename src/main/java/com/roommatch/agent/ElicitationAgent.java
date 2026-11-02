package com.roommatch.agent;

import com.roommatch.llm.LLMClient;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * F01. The State pattern's Context: runs a conversation that builds a lifestyle profile from
 * free-text answers. The agent plans as it goes: it asks about a dimension, lets the model
 * interpret the answer, skips questions the person already answered in passing, asks follow-ups
 * when an answer is vague, and gives up on a dimension rather than guess.
 * Its memory is the draft profile plus the recent question/answer exchanges.
 */
public class ElicitationAgent {
    static final int MAX_CLARIFICATIONS = 2;

    private static final Map<String, String> OPENING_QUESTIONS = Map.of(
            "cleanliness", "How tidy do you keep shared spaces like the kitchen and bathroom, "
                    + "and how do you feel when a housemate leaves a mess?",
            "noiseTolerance", "How do you feel about noise at home, such as music, TV or friends "
                    + "talking, especially in the evening?",
            "guestFrequency", "How often do you have guests over, and how do you feel about "
                    + "housemates having people over?",
            "sleepSchedule", "What does your typical sleep schedule look like, and does it change on weekends?");

    private final AnswerInterpreter interpreter;
    private final LifestyleProfile draft = new LifestyleProfile();
    private final Set<String> unresolved = new LinkedHashSet<>();
    private final List<Exchange> history = new ArrayList<>();
    private ElicitationState state;

    public ElicitationAgent(LLMClient llm) {
        this.interpreter = new AnswerInterpreter(llm);
    }

    /** Begins the conversation and returns the first question. No model call is needed yet. */
    public String start() {
        if (state != null) {
            throw new IllegalStateException("The conversation has already started.");
        }
        return advance();
    }

    /** Takes the person's answer and returns what the agent says next (a question, a follow-up, or a wrap-up). */
    public String handleResponse(String answer) {
        requireStarted();
        if (answer == null || answer.isBlank()) {
            return "I did not catch that. " + state.prompt(this);
        }
        return state.handle(this, answer.trim());
    }

    public boolean isStarted() {
        return state != null;
    }

    public boolean isComplete() {
        return state instanceof CompleteState;
    }

    /** What the agent is currently waiting to be answered. */
    public String currentPrompt() {
        requireStarted();
        return state.prompt(this);
    }

    /** Dimension to value, in the order they were first recorded. A copy: safe to keep. */
    public Map<String, String> getDraftAnswers() {
        return new LinkedHashMap<>(draft.asMap());
    }

    /** Dimensions with no recorded value yet: unanswered or given up on. */
    public List<String> missingDimensions() {
        return draft.missingDimensions();
    }

    /** Lets the person fix or fill in a value themselves instead of trusting the AI's reading (F01 error case). */
    public void correct(String dimension, String value) {
        requireStarted();
        draft.set(dimension, value);
        unresolved.remove(dimension);
        if (!(state instanceof CompleteState) && nextDimension().isEmpty()) {
            state = new CompleteState();
        }
    }

    /**
     * Saves the profile to the user, complete or not: stopping early keeps the progress made, and
     * the gaps stay visible through missingDimensions(). The user gets a copy, so later edits to
     * the draft do not silently change the saved profile. Attaching notifies the user's observers.
     */
    public LifestyleProfile finish(User user) {
        Objects.requireNonNull(user, "user");
        LifestyleProfile copy = new LifestyleProfile();
        draft.asMap().forEach(copy::set);
        user.attachLifestyleProfile(copy);
        return copy;
    }

    // ---- hooks used by the state classes (same package only) ----

    AnswerInterpreter interpreter() {
        return interpreter;
    }

    List<Exchange> history() {
        return Collections.unmodifiableList(history);
    }

    void setState(ElicitationState next) {
        this.state = next;
    }

    /** Stores everything the model extracted and logs the exchange. Called only after a successful model call. */
    void remember(String question, String answer, Interpretation result) {
        history.add(new Exchange(question, answer));
        for (String dimension : LifestyleProfile.DIMENSIONS) {   // fixed order keeps summaries stable
            String value = result.dimensions().get(dimension);
            if (value != null) {
                draft.set(dimension, value);
                unresolved.remove(dimension);
            }
        }
    }

    void markUnresolved(String dimension) {
        unresolved.add(dimension);
    }

    /** Moves to the next dimension still needing an answer, or to the end. Returns what to say. */
    String advance() {
        Optional<String> next = nextDimension();
        if (next.isPresent()) {
            AskingDimensionState asking = new AskingDimensionState(next.get());
            state = asking;
            return asking.prompt(this);
        }
        state = new CompleteState();
        return completionMessage();
    }

    String completionMessage() {
        StringBuilder m = new StringBuilder("That is everything I need. Here is what I understood:\n");
        draft.asMap().forEach((d, v) -> m.append("  ").append(d).append(": ").append(v).append('\n'));
        List<String> missing = draft.missingDimensions();
        if (!missing.isEmpty()) {
            m.append("I could not pin down: ").append(String.join(", ", missing))
                    .append(". You can fill these in yourself before ranking.\n");
        }
        m.append("Correct anything that is wrong, or confirm to save.");
        return m.toString();
    }

    static String openingQuestion(String dimension) {
        return OPENING_QUESTIONS.get(dimension);
    }

    private Optional<String> nextDimension() {
        return LifestyleProfile.DIMENSIONS.stream()
                .filter(d -> draft.get(d).isEmpty() && !unresolved.contains(d))
                .findFirst();
    }

    private void requireStarted() {
        if (state == null) {
            throw new IllegalStateException("Call start() before answering.");
        }
    }
}
