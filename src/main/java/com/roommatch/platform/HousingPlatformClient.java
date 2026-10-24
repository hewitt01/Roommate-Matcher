package com.roommatch.platform;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Adaptee (Adapter pattern): stands in for the host housing platform's own API.
 * RoomMatch does not control this interface, so nothing else in the project talks to it
 * except PlatformProfileAdapter. For the course project the "platform" is a JSON file of
 * fake users; swapping in a real platform would only change this class.
 */
public class HousingPlatformClient {
    private final List<PlatformUserRecord> records;

    public HousingPlatformClient(List<PlatformUserRecord> records) {
        this.records = List.copyOf(Objects.requireNonNull(records, "records"));
    }

    /** Loads platform data from a JSON array file. */
    public static HousingPlatformClient fromJsonFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            PlatformUserRecord[] loaded = new Gson().fromJson(reader, PlatformUserRecord[].class);
            if (loaded == null) {
                throw new PlatformException("Platform data file is empty: " + file);
            }
            return new HousingPlatformClient(List.of(loaded));
        } catch (IOException e) {
            throw new PlatformException("Cannot read platform data file: " + file, e);
        } catch (JsonParseException e) {
            throw new PlatformException("Platform data file is not valid JSON: " + file, e);
        }
    }

    /** One user's record by id, whatever their status (so callers can see deactivated users). */
    public Optional<PlatformUserRecord> fetchUserRecord(String userId) {
        return records.stream().filter(r -> Objects.equals(r.getUserId(), userId)).findFirst();
    }

    /**
     * Searches active users matching the filter. A record that lacks a value the filter
     * constrains (for example no rent figure when a rent range is requested) does not match.
     */
    public List<PlatformUserRecord> listCandidates(PlatformSearchFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return records.stream().filter(PlatformUserRecord::isActive).filter(r -> matches(r, filter)).toList();
    }

    private static boolean matches(PlatformUserRecord r, PlatformSearchFilter f) {
        if (f.getCity() != null && !f.getCity().equalsIgnoreCase(r.getCity())) {
            return false;
        }
        if (f.getRentMin() != null && (r.getRentMax() == null || r.getRentMax() < f.getRentMin())) {
            return false;
        }
        if (f.getRentMax() != null && (r.getRentMax() == null || r.getRentMax() > f.getRentMax())) {
            return false;
        }
        if (f.getAvailableFrom() != null || f.getAvailableTo() != null) {
            LocalDate available = parseDate(r.getAvailableFrom());
            if (available == null) {
                return false;
            }
            if (f.getAvailableFrom() != null && available.isBefore(LocalDate.parse(f.getAvailableFrom()))) {
                return false;
            }
            if (f.getAvailableTo() != null && available.isAfter(LocalDate.parse(f.getAvailableTo()))) {
                return false;
            }
        }
        return true;
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
