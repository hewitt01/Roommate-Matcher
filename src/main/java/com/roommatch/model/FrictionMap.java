package com.roommatch.model;

import java.util.List;

/** Likely friction points and suggested house rules for one matched pair (F06). Immutable. */
public class FrictionMap {
    private final List<String> frictionPoints;
    private final List<String> suggestedRules;

    public FrictionMap(List<String> frictionPoints, List<String> suggestedRules) {
        this.frictionPoints = List.copyOf(frictionPoints);
        this.suggestedRules = List.copyOf(suggestedRules);
    }

    public List<String> getFrictionPoints() { return frictionPoints; }
    public List<String> getSuggestedRules() { return suggestedRules; }
}
