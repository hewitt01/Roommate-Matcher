package com.roommatch.model;

import java.util.List;

/**
 * Likely friction points and suggested house rules for one matched pair (F06). Immutable.
 * notAssessedDimensions lists dimensions left out because one side had no data, so a partial
 * map is clearly labelled as partial.
 */
public class FrictionMap {
    private final List<String> frictionPoints;
    private final List<String> suggestedRules;
    private final List<String> notAssessedDimensions;

    public FrictionMap(List<String> frictionPoints, List<String> suggestedRules) {
        this(frictionPoints, suggestedRules, List.of());
    }

    public FrictionMap(List<String> frictionPoints, List<String> suggestedRules, List<String> notAssessedDimensions) {
        this.frictionPoints = List.copyOf(frictionPoints);
        this.suggestedRules = List.copyOf(suggestedRules);
        this.notAssessedDimensions = List.copyOf(notAssessedDimensions);
    }

    public List<String> getFrictionPoints() { return frictionPoints; }
    public List<String> getSuggestedRules() { return suggestedRules; }
    public List<String> getNotAssessedDimensions() { return notAssessedDimensions; }
}
