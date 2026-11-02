package com.roommatch.agent;

/** Concrete state: nothing left to ask. Further answers are ignored (no model call, no cost). */
class CompleteState implements ElicitationState {
    @Override
    public String handle(ElicitationAgent agent, String answer) {
        return "We already have everything we need. Review your profile, correct anything, or confirm it.";
    }

    @Override
    public String prompt(ElicitationAgent agent) {
        return agent.completionMessage();
    }
}
