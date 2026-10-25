package com.roommatch.agent;

import com.roommatch.model.HardFilters;
import com.roommatch.model.User;

/** Input to ProfileAccessTool: who is asking, and which hard filters apply. */
public record ProfileQuery(User requester, HardFilters filters) { }
