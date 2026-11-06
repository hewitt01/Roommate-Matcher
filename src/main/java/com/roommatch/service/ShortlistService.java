package com.roommatch.service;

import com.roommatch.model.Shortlist.AddResult;
import com.roommatch.model.User;
import com.roommatch.repository.CandidateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * F09. Plain deterministic bookmarking, no AI. The stored ids are the source of truth, so a
 * bookmarked person who later disappears or is deactivated still shows up in the list, clearly
 * marked "no longer available", instead of silently vanishing.
 */
public class ShortlistService {
    /** One line of the shortlist. user is null if the platform no longer knows the candidate. */
    public record Entry(String id, User user, boolean available) { }

    private final CandidateRepository repository;
    private final ShortlistStore store;

    public ShortlistService(CandidateRepository repository, ShortlistStore store) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Loads the saved bookmarks into the user's in-memory Shortlist (only candidates still available). */
    public void restore(User user) {
        for (String id : store.load(user.getId())) {
            repository.getProfile(id).ifPresent(c -> user.getShortlist().add(c));
        }
    }

    public AddResult add(User user, User candidate) {
        AddResult result = user.getShortlist().add(candidate);
        if (result != AddResult.UNAVAILABLE) {
            List<String> ids = new ArrayList<>(store.load(user.getId()));
            if (!ids.contains(candidate.getId())) {
                ids.add(candidate.getId());
                store.save(user.getId(), ids);
            }
        }
        return result;
    }

    /** @return true if the candidate was on the shortlist */
    public boolean remove(User user, String candidateId) {
        List<String> ids = new ArrayList<>(store.load(user.getId()));
        boolean wasThere = ids.remove(candidateId);
        if (wasThere) {
            store.save(user.getId(), ids);
        }
        user.getShortlist().getBookmarked().stream()
                .filter(c -> c.getId().equals(candidateId)).findFirst()
                .ifPresent(c -> user.getShortlist().remove(c));
        return wasThere;
    }

    public List<Entry> list(User user) {
        List<Entry> entries = new ArrayList<>();
        for (String id : store.load(user.getId())) {
            Optional<User> found = repository.getProfile(id);
            entries.add(new Entry(id, found.orElse(null), found.map(User::isActive).orElse(false)));
        }
        return entries;
    }
}
