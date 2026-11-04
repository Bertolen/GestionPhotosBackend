package com.example.gestionphotos.integration;

import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.gestionphotos.controller.PhotoController;
import com.example.gestionphotos.service.FileStorageService;
import com.example.gestionphotos.service.PhotoService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test bout-en-bout de l'upload avec date de creation forcee :
 * cable le vrai PhotoService, le vrai FileStorageService et le vrai
 * PhotoController (sans Spring context), puis verifie la date affichee
 * par le listing suivant l'upload.
 */
class PhotoUploadEndToEndTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        FileStorageService fileStorageService = new FileStorageService(
                tempDir.resolve("photos-storage").toString());
        PhotoService photoService = new PhotoService(fileStorageService);
        ReflectionTestUtils.setField(photoService, "maxFileSize", 10L * 1024L * 1024L);
        ReflectionTestUtils.setField(photoService, "allowedMimeTypes",
                new String[]{"image/jpeg", "image/png", "image/gif", "image/webp"});

        PhotoController photoController = new PhotoController(photoService, fileStorageService);
        photoController.setMaxFilesPerUpload(10);
        mockMvc = MockMvcBuilders.standaloneSetup(photoController).build();
    }

    @Test
    void uploadWithForcedDateThenList_showsForcedCreationDate() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "contenu photo".getBytes());

        // Act - upload avec une date de creation forcee
        mockMvc.perform(multipart("/api/photos/upload/single-with-date")
                        .file(file)
                        .param("creationDate", "2023-06-15T10:30:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creationDate").value("2023-06-15T10:30:00"));

        // Assert - le listing doit afficher la date forcee, pas une date re-derivee
        mockMvc.perform(get("/api/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].creationDate").value("2023-06-15T10:30:00"));
    }

    @Test
    void uploadMultipleWithForcedDatesThenList_showsForcedCreationDates() throws Exception {
        // Arrange
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "vacances.jpg", "image/jpeg", "contenu photo 1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "anniversaire.jpg", "image/jpeg", "contenu photo 2".getBytes());

        // Act - upload multiple avec dates forcees
        mockMvc.perform(multipart("/api/photos/upload/multiple-with-date")
                        .file(file1)
                        .file(file2)
                        .param("creationDates", "2023-06-15T10:30:00")
                        .param("creationDates", "2023-08-02T18:45:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Assert - le listing affiche les dates forcees pour les deux photos
        mockMvc.perform(get("/api/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].creationDate").value("2023-08-02T18:45:00"))
                .andExpect(jsonPath("$[1].creationDate").value("2023-06-15T10:30:00"));
    }

    @Test
    void uploadWithoutForcedDateThenList_showsTimestampFromOriginalName() throws Exception {
        // Arrange - nom original au format Android YYYYMMDD_HHmmss
        MockMultipartFile file = new MockMultipartFile(
                "file", "20240922_123045.jpg", "image/jpeg", "contenu photo".getBytes());

        // Act - upload sans date forcee
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(file))
                .andExpect(status().isOk());

        // Assert - le listing affiche la date extraite du nom original
        mockMvc.perform(get("/api/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].creationDate").value("2024-09-22T12:30:45"));
    }
}
