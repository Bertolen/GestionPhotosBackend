package com.example.gestionphotos.exception;

/**
 * Signale qu'une photo est déjà présente dans le stockage.
 */
public class DuplicatePhotoException extends RuntimeException {

    public DuplicatePhotoException(String message) {
        super(message);
    }
}
