package com.roommatch.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileShortlistStoreTest {
    @Test
    void missingFileMeansNothingSavedYet(@TempDir Path dir) {
        assertTrue(new FileShortlistStore(dir.resolve("none.json")).load("u1").isEmpty());
    }

    @Test
    void savedIdsComeBackInOrderAndSurviveANewStoreObject(@TempDir Path dir) {
        Path file = dir.resolve("shortlists.json");
        new FileShortlistStore(file).save("u1", List.of("p3", "p1", "p2"));
        assertEquals(List.of("p3", "p1", "p2"), new FileShortlistStore(file).load("u1"));
    }

    @Test
    void usersAreKeptSeparate(@TempDir Path dir) {
        FileShortlistStore s = new FileShortlistStore(dir.resolve("s.json"));
        s.save("u1", List.of("a"));
        s.save("u2", List.of("b", "c"));
        assertEquals(List.of("a"), s.load("u1"));
        assertEquals(List.of("b", "c"), s.load("u2"));
        assertTrue(s.load("u3").isEmpty());
    }

    @Test
    void savingAnEmptyListForgetsTheUser(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("s.json");
        FileShortlistStore s = new FileShortlistStore(file);
        s.save("u1", List.of("a"));
        s.save("u1", List.of());
        assertTrue(s.load("u1").isEmpty());
        assertFalse(Files.readString(file).contains("u1"));
    }

    @Test
    void parentFoldersAreCreatedAndNoTempFilesAreLeftBehind(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested").resolve("deeper").resolve("s.json");
        new FileShortlistStore(file).save("u1", List.of("a"));
        assertTrue(Files.isRegularFile(file));
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void aBrokenFileIsReportedNotSilentlyOverwritten(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("s.json");
        Files.writeString(file, "{ not json");
        FileShortlistStore s = new FileShortlistStore(file);
        assertThrows(ShortlistStoreException.class, () -> s.load("u1"));
        assertThrows(ShortlistStoreException.class, () -> s.save("u1", List.of("a")));
        assertEquals("{ not json", Files.readString(file), "the damaged file is left untouched");
    }

    @Test
    void anEmptyFileCountsAsNothingSaved(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("s.json");
        Files.writeString(file, "");
        assertTrue(new FileShortlistStore(file).load("u1").isEmpty());
    }
}
