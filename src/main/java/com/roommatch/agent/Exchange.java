package com.roommatch.agent;

/** One question the agent asked and the answer it got: the agent's short-term conversation memory. */
public record Exchange(String question, String answer) { }
