package com.roommatch.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Keeps every user's shortlist in one small JSON file. A missing file just means nobody has bookmarked yet. */
public class FileShortlistStore implements ShortlistStore {
    private static final Type FILE_SHAPE = new TypeToken<LinkedHashMap<String, List<String>>>() { }.getType();

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public FileShortlistStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    @Override
    public List<String> load(String userId) {
        List<String> ids = readAll().get(userId);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    @Override
    public void save(String userId, List<String> candidateIds) {
        Map<String, List<String>> all = readAll();
        if (candidateIds.isEmpty()) {
            all.remove(userId);
        } else {
            all.put(userId, List.copyOf(candidateIds));
        }
        write(all);
    }

    private Map<String, List<String>> readAll() {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, List<String>> all = gson.fromJson(reader, FILE_SHAPE);
            return all == null ? new LinkedHashMap<>() : all;
        } catch (IOException | JsonParseException e) {
            throw new ShortlistStoreException("The shortlist file " + file + " could not be read. "
                    + "Fix or delete it to continue.", e);
        }
    }

    /** Writes to a temporary file first, then swaps it in, so a crash cannot leave half a file. */
    private void write(Map<String, List<String>> all) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "shortlists", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(all, writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ShortlistStoreException("The shortlist could not be saved to " + file + ".", e);
        }
    }
}
