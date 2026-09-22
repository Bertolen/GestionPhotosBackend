package com.example.gestionphotos.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaires pour extraire la date de prise de vue des noms de photos.
 */
public final class PhotoTimestampUtils {

    private static final DateTimeFormatter PHOTO_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss")
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern PHOTO_FILENAME_PATTERN =
            Pattern.compile("^(\\d{8}_\\d{6})(?:_[0-9a-fA-F]{8})?\\.[^.]+$");

    private PhotoTimestampUtils() {
    }

    /**
     * Extrait la date de prise de vue d'un nom de photo Android.
     *
     * @param filename le nom du fichier
     * @return la date extraite si le nom respecte la convention
     *         YYYYMMDD_HHmmss.ext, avec éventuellement un suffixe UUID
     */
    public static Optional<LocalDateTime> extractPhotoTimestamp(String filename) {
        if (filename == null) {
            return Optional.empty();
        }

        Matcher matcher = PHOTO_FILENAME_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDateTime.parse(matcher.group(1), PHOTO_TIMESTAMP_FORMATTER));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
