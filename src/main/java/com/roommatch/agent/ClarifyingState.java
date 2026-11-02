package com.roommatch.agent;

/**
 * Concrete state: the last answer was too vague, so the agent asked a follow-up. It never guesses:
 * after MAX_CLARIFICATIONS unhelpful answers the dimension is left unresolved and flagged instead.
 */
class ClarifyingState implements ElicitationState {
    private final String dimension;
    private final int attempts;
    private final String followUp;

    ClarifyingState(String dimension, int attempts, String followUp) {
        this.dimension = dimension;
        this.attempts = attempts;
        this.followUp = followUp;
    }

    @Override
    public String handle(ElicitationAgent agent, String answer) {
        Interpretation result = agent.interpreter().interpret(
                dimension, followUp, answer, agent.getDraftAnswers(), agent.history());
        agent.remember(followUp, answer, result);
        if (result.covers(dimension)) {
            return agent.advance();
        }
        if (attempts >= ElicitationAgent.MAX_CLARIFICATIONS) {
            agent.markUnresolved(dimension);
            return "No problem, we will leave " + dimension + " for now. " + agent.advance();
        }
        ClarifyingState next = new ClarifyingState(dimension, attempts + 1, result.followUp());
        agent.setState(next);
        return next.prompt(agent);
    }

    @Override
    public String prompt(ElicitationAgent agent) {
        return followUp;
    }
}
