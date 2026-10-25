package com.roommatch.agent;

import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.User;

/**
 * Strategy interface: one way of judging how well a requester and a candidate would live
 * together. The ranking agent only knows this interface, so the scoring method can be
 * swapped (LLM-based, rule-based, a test double) without touching the agent.
 */
public interface CompatibilityScorer {
    /**
     * @return a result holding two separate directional scores for this one candidate
     * @throws InsufficientDataException if there is nothing comparable between the two users
     * @throws ScoringException if a score could not be produced
     */
    CompatibilityResult score(User requester, User candidate);
}
