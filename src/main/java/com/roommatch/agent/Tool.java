package com.roommatch.agent;

/** Something the agent can decide to use: a named capability with typed input and output. */
public interface Tool<I, O> {
    String name();

    O execute(I input);
}
