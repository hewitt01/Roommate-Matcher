package com.roommatch.agent;

import com.roommatch.model.CompatibilityResult;

/**
 * An explanation plus the result it describes. The result is included because it may be a
 * freshly re-scored one whose numbers differ from the ranking the person saw (rescored = true).
 */
public record MatchExplanation(CompatibilityResult result, String text, boolean rescored) { }
