package com.example.gestionphotos.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoTimestampUtilsTest {

    @Test
    void extractPhotoTimestamp_shouldExtractTimestampFromAndroidFilename() {
        assertEquals(
                LocalDateTime.of(2026, 9, 20, 19, 18, 17),
                PhotoTimestampUtils.extractPhotoTimestamp("20260920_191817.jpg").orElseThrow());
    }

    @Test
    void extractPhotoTimestamp_shouldExtractTimestampFromStoredFilename() {
        assertEquals(
                LocalDateTime.of(2026, 9, 20, 19, 18, 17),
                PhotoTimestampUtils.extractPhotoTimestamp("20260920_191817_ab12cd34.jpg").orElseThrow());
    }

    @Test
    void extractPhotoTimestamp_shouldRejectInvalidTimestamp() {
        assertTrue(PhotoTimestampUtils.extractPhotoTimestamp("20261320_191817.jpg").isEmpty());
    }
}
