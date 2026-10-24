package com.roommatch.repository;

import com.roommatch.model.HardFilters;
import com.roommatch.model.User;
import java.util.List;
import java.util.Optional;

/** The Target interface (Adapter pattern): how RoomMatch itself wants to ask for candidates. */
public interface CandidateRepository {
    /** Active candidates passing the hard filters, never including the requesting user. */
    List<User> getCandidates(User user, HardFilters filters);

    /** One user by platform id, including deactivated ones (they come back with isActive() false). */
    Optional<User> getProfile(String id);
}
