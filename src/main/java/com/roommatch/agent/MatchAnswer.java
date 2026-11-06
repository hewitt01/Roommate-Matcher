package com.roommatch.agent;

import com.roommatch.model.CompatibilityResult;

/** The answer to a question about a match. answerable is false when the profile data does not cover it. */
public record MatchAnswer(CompatibilityResult result, String text, boolean answerable, boolean rescored) { }
