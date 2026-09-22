package com.example.gestionphotos.exception;

import com.example.gestionphotos.dto.PhotoDto;

import java.util.List;

/**
 * Regroupe les erreurs rencontrées pendant un upload multiple.
 */
public class MultiplePhotoUploadException extends RuntimeException {

    private final List<PhotoDto> uploadedPhotos;
    private final List<Exception> errors;

    public MultiplePhotoUploadException(List<PhotoDto> uploadedPhotos, List<Exception> errors) {
        super("Certaines photos n'ont pas pu être traitées");
        this.uploadedPhotos = List.copyOf(uploadedPhotos);
        this.errors = List.copyOf(errors);
    }

    public List<PhotoDto> getUploadedPhotos() {
        return uploadedPhotos;
    }

    public List<Exception> getErrors() {
        return errors;
    }
}
