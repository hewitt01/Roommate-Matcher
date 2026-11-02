package com.roommatch.agent;

/** Concrete state: waiting for the answer to the opening question about one dimension. */
class AskingDimensionState implements ElicitationState {
    private final String dimension;

    AskingDimensionState(String dimension) {
        this.dimension = dimension;
    }

    @Override
    public String handle(ElicitationAgent agent, String answer) {
        String question = prompt(agent);
        Interpretation result = agent.interpreter().interpret(
                dimension, question, answer, agent.getDraftAnswers(), agent.history());
        agent.remember(question, answer, result);
        if (result.covers(dimension)) {
            return agent.advance();
        }
        ClarifyingState next = new ClarifyingState(dimension, 1, result.followUp());
        agent.setState(next);
        return next.prompt(agent);
    }

    @Override
    public String prompt(ElicitationAgent agent) {
        return ElicitationAgent.openingQuestion(dimension);
    }
}
