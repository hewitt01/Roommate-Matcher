package com.roommatch.agent;

import com.roommatch.model.User;
import com.roommatch.repository.CandidateRepository;
import java.util.List;
import java.util.Objects;

/** The agent's retrieval tool: fetches the eligible candidate pool through the repository. */
public class ProfileAccessTool implements Tool<ProfileQuery, List<User>> {
    public static final String NAME = "profile-access";

    private final CandidateRepository repository;

    public ProfileAccessTool(CandidateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<User> execute(ProfileQuery query) {
        return repository.getCandidates(query.requester(), query.filters());
    }
}
