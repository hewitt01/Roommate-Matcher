package com.roommatch.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HousingPlatformClientTest {
    private static PlatformUserRecord rec(String id, String city, Double rent, String from, String status) {
        return new PlatformUserRecord(id, city, rent, from, status, Map.of());
    }

    private HousingPlatformClient client() {
        return new HousingPlatformClient(List.of(
                rec("a", "Toronto", 1000.0, "2026-12-01", "ACTIVE"),
                rec("b", "toronto", 1500.0, "2027-01-15", "ACTIVE"),
                rec("c", "Vaughan", 900.0, "2026-11-20", "ACTIVE"),
                rec("d", "Toronto", 1100.0, "2026-12-05", "DEACTIVATED"),
                rec("e", "Toronto", null, "2026-12-05", "ACTIVE")));
    }

    private static String ids(List<PlatformUserRecord> records) {
        return records.stream().map(PlatformUserRecord::getUserId).sorted().collect(Collectors.joining(","));
    }

    private static PlatformSearchFilter f(String city, Double min, Double max, String from, String to) {
        return new PlatformSearchFilter(city, min, max, from, to);
    }

    @Test
    void emptyFilterReturnsEveryActiveUser() {
        assertEquals("a,b,c,e", ids(client().listCandidates(f(null, null, null, null, null))));
    }

    @Test
    void cityFilterIgnoresCase() {
        assertEquals("a,b,e", ids(client().listCandidates(f("TORONTO", null, null, null, null))));
    }

    @Test
    void rentRangeIsInclusiveAndExcludesRecordsWithNoRent() {
        assertEquals("a", ids(client().listCandidates(f(null, 1000.0, 1000.0, null, null))));
        assertEquals("a,b,c", ids(client().listCandidates(f(null, 0.0, 2000.0, null, null))));
    }

    @Test
    void moveInWindowIsInclusive() {
        assertEquals("a,e", ids(client().listCandidates(f(null, null, null, "2026-12-01", "2026-12-31"))));
        assertEquals("a,b,e", ids(client().listCandidates(f(null, null, null, "2026-12-01", null))));
    }

    @Test
    void filtersCombine() {
        assertEquals("a", ids(client().listCandidates(f("Toronto", 900.0, 1200.0, "2026-11-01", "2026-12-31"))));
        assertEquals("", ids(client().listCandidates(f("Vaughan", 1000.0, null, null, null))));
    }

    @Test
    void fetchByIdReturnsDeactivatedUsersToo() {
        assertEquals("DEACTIVATED", client().fetchUserRecord("d").orElseThrow().getStatus());
        assertTrue(client().fetchUserRecord("nope").isEmpty());
    }

    @Test
    void loadsRecordsFromJsonFileUsingPlatformFieldNames(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("users.json");
        Files.writeString(file, """
                [{"user_id":"x1","city":"Toronto","rent_max":950,"available_from":"2026-12-01",
                  "status":"ACTIVE","habits":{"tidiness":"tidy"}}]""");
        PlatformUserRecord r = HousingPlatformClient.fromJsonFile(file).fetchUserRecord("x1").orElseThrow();
        assertEquals(950.0, r.getRentMax());
        assertEquals("2026-12-01", r.getAvailableFrom());
        assertEquals("tidy", r.getHabits().get("tidiness"));
    }

    @Test
    void missingOrBrokenFilesGiveAClearException(@TempDir Path dir) throws IOException {
        assertThrows(PlatformException.class, () -> HousingPlatformClient.fromJsonFile(dir.resolve("absent.json")));
        Path bad = dir.resolve("bad.json");
        Files.writeString(bad, "{ this is not json");
        assertThrows(PlatformException.class, () -> HousingPlatformClient.fromJsonFile(bad));
        Path empty = dir.resolve("empty.json");
        Files.writeString(empty, "");
        assertThrows(PlatformException.class, () -> HousingPlatformClient.fromJsonFile(empty));
    }
}
