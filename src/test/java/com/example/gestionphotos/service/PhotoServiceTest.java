package com.example.gestionphotos.service;

import com.example.gestionphotos.dto.PhotoDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour PhotoService.
 */
@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private PhotoService photoService;

    @BeforeEach
    void setUp() {
        // Initialiser les champs injectés par @Value pour éviter les valeurs par défaut (0 octets)
        ReflectionTestUtils.setField(photoService, "maxFileSize", 10L * 1024L * 1024L); // 10MB
        ReflectionTestUtils.setField(photoService, "allowedMimeTypes", 
                new String[]{"image/jpeg", "image/png", "image/gif", "image/webp"});
    }

    private static final String TEST_STORED_PATH = "photos/2024/09/22/test_2024-09-22_12-30-45_ab123456.jpg";
    private static final String TEST_UUID = "ab123456";
    private static final String TEST_ORIGINAL_NAME = "test.jpg";
    private static final String TEST_FILE_NAME = "test_2024-09-22_12-30-45_ab123456.jpg";
    private static final long TEST_SIZE = 1024L;
    private static final String TEST_MIME_TYPE = "image/jpeg";


    // ==================== Tests pour uploadPhoto ====================

    @Test
    void uploadPhoto_shouldCreateDtoWithAllMetadata() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn(TEST_ORIGINAL_NAME);
        when(multipartFile.getSize()).thenReturn(TEST_SIZE);
        when(multipartFile.getContentType()).thenReturn(TEST_MIME_TYPE);
        when(fileStorageService.store(multipartFile)).thenReturn(TEST_STORED_PATH);
        when(fileStorageService.load(TEST_STORED_PATH)).thenReturn(Path.of(TEST_STORED_PATH));

        // Act
        PhotoDto result = photoService.uploadPhoto(multipartFile);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_UUID, result.id());
        assertEquals(TEST_ORIGINAL_NAME, result.originalName());
        assertEquals(TEST_STORED_PATH, result.storedPath());
        assertEquals(TEST_FILE_NAME, result.fileName());
        assertEquals(TEST_SIZE, result.size());
        assertEquals(TEST_MIME_TYPE, result.mimeType());
        assertNotNull(result.uploadDate());
        assertNotNull(result.creationDate());
    }

    @Test
    void uploadPhoto_shouldUseUploadDateAsCreationDateWhenFileDateUnavailable() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn(TEST_ORIGINAL_NAME);
        when(multipartFile.getSize()).thenReturn(TEST_SIZE);
        when(multipartFile.getContentType()).thenReturn(TEST_MIME_TYPE);
        when(fileStorageService.store(multipartFile)).thenReturn(TEST_STORED_PATH);
        // Retourner un chemin inexistant pour que Files.getLastModifiedTime jetter IOException
        when(fileStorageService.load(TEST_STORED_PATH)).thenReturn(Path.of("nonexistent/path/file.jpg"));

        // Act
        PhotoDto result = photoService.uploadPhoto(multipartFile);

        // Assert
        assertNotNull(result);
        assertEquals(result.uploadDate(), result.creationDate());
    }


    // ==================== Tests pour uploadPhotos ====================

    @Test
    void uploadPhotos_shouldUploadMultipleNonEmptyFiles() throws IOException {
        // Arrange
        MultipartFile[] files = new MultipartFile[]{multipartFile, multipartFile};
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn(TEST_ORIGINAL_NAME);
        when(multipartFile.getSize()).thenReturn(TEST_SIZE);
        when(multipartFile.getContentType()).thenReturn(TEST_MIME_TYPE);
        when(fileStorageService.store(multipartFile))
                .thenReturn(TEST_STORED_PATH + "_1")
                .thenReturn(TEST_STORED_PATH + "_2");
        when(fileStorageService.load(anyString())).thenReturn(Path.of(TEST_STORED_PATH));

        // Act
        List<PhotoDto> result = photoService.uploadPhotos(files);

        // Assert
        assertEquals(2, result.size());
        // Les UUIDs sont extraits des noms de fichiers : test_2024-09-22_12-30-45_ab123456_1 -> ab123456
        assertEquals(TEST_UUID, result.get(0).id());
        assertEquals(TEST_UUID, result.get(1).id());
    }

    @Test
    void uploadPhotos_shouldSkipEmptyFiles() throws IOException {
        // Arrange
        MultipartFile emptyFile = mock(MultipartFile.class);
        MultipartFile validFile = mock(MultipartFile.class);
        MultipartFile[] files = new MultipartFile[]{emptyFile, validFile};

        when(emptyFile.isEmpty()).thenReturn(true);
        when(validFile.isEmpty()).thenReturn(false);
        when(validFile.getOriginalFilename()).thenReturn(TEST_ORIGINAL_NAME);
        when(validFile.getSize()).thenReturn(TEST_SIZE);
        when(validFile.getContentType()).thenReturn(TEST_MIME_TYPE);
        when(fileStorageService.store(validFile)).thenReturn(TEST_STORED_PATH);
        when(fileStorageService.load(TEST_STORED_PATH)).thenReturn(Path.of(TEST_STORED_PATH));

        // Act
        List<PhotoDto> result = photoService.uploadPhotos(files);

        // Assert
        assertEquals(1, result.size());
        assertEquals(TEST_UUID, result.get(0).id());
        verify(fileStorageService, times(1)).store(validFile);
        verify(fileStorageService, never()).store(emptyFile);
    }

    @Test
    void uploadPhotos_shouldReturnEmptyListWhenAllFilesEmpty() throws IOException {
        // Arrange
        MultipartFile emptyFile1 = mock(MultipartFile.class);
        MultipartFile emptyFile2 = mock(MultipartFile.class);
        when(emptyFile1.isEmpty()).thenReturn(true);
        when(emptyFile2.isEmpty()).thenReturn(true);

        // Act
        List<PhotoDto> result = photoService.uploadPhotos(new MultipartFile[]{emptyFile1, emptyFile2});

        // Assert
        assertTrue(result.isEmpty());
        verify(fileStorageService, never()).store(any());
    }


    // ==================== Tests pour getPhotoById ====================

    @Test
    void getPhotoById_shouldReturnCorrectPhoto() {
        // Arrange
        PhotoDto photo1 = new PhotoDto("id1", "name1.jpg", "path1", "file1.jpg", 100L, "image/jpeg",
                LocalDateTime.now(), LocalDateTime.now());
        PhotoDto photo2 = new PhotoDto("id2", "name2.jpg", "path2", "file2.jpg", 200L, "image/jpeg",
                LocalDateTime.now(), LocalDateTime.now());

        PhotoService spyService = spy(photoService);
        doReturn(new ArrayList<>(List.of(photo1, photo2))).when(spyService).getAllPhotos();

        // Act
        PhotoDto result = spyService.getPhotoById("id2");

        // Assert
        assertNotNull(result);
        assertEquals("id2", result.id());
        assertEquals("name2.jpg", result.originalName());
    }

    @Test
    void getPhotoById_shouldReturnNullWhenNotFound() {
        // Arrange
        PhotoDto photo1 = new PhotoDto("id1", "name1.jpg", "path1", "file1.jpg", 100L, "image/jpeg",
                LocalDateTime.now(), LocalDateTime.now());

        PhotoService spyService = spy(photoService);
        doReturn(new ArrayList<>(List.of(photo1))).when(spyService).getAllPhotos();

        // Act
        PhotoDto result = spyService.getPhotoById("non-existent-id");

        // Assert
        assertNull(result);
    }


    // ==================== Tests pour deletePhoto ====================

    @Test
    void deletePhoto_shouldDeleteAndReturnTrue() throws IOException {
        // Arrange
        PhotoDto photo = new PhotoDto(TEST_UUID, TEST_ORIGINAL_NAME, TEST_STORED_PATH,
                TEST_FILE_NAME, TEST_SIZE, TEST_MIME_TYPE, LocalDateTime.now(), LocalDateTime.now());

        PhotoService spyService = spy(photoService);
        doReturn(photo).when(spyService).getPhotoById(TEST_UUID);
        doNothing().when(fileStorageService).delete(TEST_STORED_PATH);

        // Act
        boolean result = spyService.deletePhoto(TEST_UUID);

        // Assert
        assertTrue(result);
        verify(fileStorageService).delete(TEST_STORED_PATH);
    }

    @Test
    void deletePhoto_shouldReturnFalseWhenPhotoNotFound() throws IOException {
        // Arrange
        PhotoService spyService = spy(photoService);
        doReturn(null).when(spyService).getPhotoById("non-existent-id");

        // Act
        boolean result = spyService.deletePhoto("non-existent-id");

        // Assert
        assertFalse(result);
        verify(fileStorageService, never()).delete(anyString());
    }


    // ==================== Tests pour getPhotosByDateRange ====================

    @Test
    void getPhotosByDateRange_shouldFilterPhotosInRange() {
        // Arrange
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 12, 31, 23, 59);
        LocalDateTime dateInRange = LocalDateTime.of(2024, 6, 15, 12, 0);
        LocalDateTime dateOutOfRange = LocalDateTime.of(2023, 6, 15, 12, 0);

        PhotoDto photoInRange = new PhotoDto("id1", "n1.jpg", "p1", "f1.jpg", 100L, "image/jpeg",
                dateInRange, dateInRange);
        PhotoDto photoOutOfRange = new PhotoDto("id2", "n2.jpg", "p2", "f2.jpg", 200L, "image/jpeg",
                dateOutOfRange, dateOutOfRange);

        PhotoService spyService = spy(photoService);
        doReturn(new ArrayList<>(List.of(photoInRange, photoOutOfRange))).when(spyService).getAllPhotos();

        // Act
        List<PhotoDto> result = spyService.getPhotosByDateRange(from, to);

        // Assert
        assertEquals(1, result.size());
        assertEquals("id1", result.get(0).id());
    }

    @Test
    void getPhotosByDateRange_shouldReturnEmptyListWhenNoMatch() {
        // Arrange
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 12, 31, 23, 59);
        LocalDateTime dateOutOfRange = LocalDateTime.of(2023, 6, 15, 12, 0);

        PhotoDto photo = new PhotoDto("id1", "n1.jpg", "p1", "f1.jpg", 100L, "image/jpeg",
                dateOutOfRange, dateOutOfRange);

        PhotoService spyService = spy(photoService);
        doReturn(new ArrayList<>(List.of(photo))).when(spyService).getAllPhotos();

        // Act
        List<PhotoDto> result = spyService.getPhotosByDateRange(from, to);

        // Assert
        assertTrue(result.isEmpty());
    }


    // ==================== Tests pour getPhotosSortedByDate ====================

    @Test
    void getPhotosSortedByDate_shouldSortDescending() {
        // Arrange
        LocalDateTime newer = LocalDateTime.of(2024, 9, 22, 12, 0);
        LocalDateTime older = LocalDateTime.of(2024, 9, 21, 12, 0);

        PhotoDto newerPhoto = new PhotoDto("id-newer", "newer.jpg", "path-newer", "file-newer.jpg",
                200L, "image/jpeg", newer, newer);
        PhotoDto olderPhoto = new PhotoDto("id-older", "older.jpg", "path-older", "file-older.jpg",
                100L, "image/jpeg", older, older);

        PhotoService spyService = spy(photoService);
        doReturn(new ArrayList<>(List.of(olderPhoto, newerPhoto))).when(spyService).getAllPhotos();

        // Act
        List<PhotoDto> result = spyService.getPhotosSortedByDate();

        // Assert
        assertEquals(2, result.size());
        assertEquals("id-newer", result.get(0).id());
        assertEquals("id-older", result.get(1).id());
    }


    // ==================== Tests pour la validation (via uploadPhoto) ====================

    @Test
    void uploadPhoto_shouldThrowWhenFileIsNull() {
        try {
            photoService.uploadPhoto(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException e) {
            fail("Unexpected IOException");
        }
    }

    @Test
    void uploadPhoto_shouldThrowWhenFileIsEmpty() {
        // Arrange
        when(multipartFile.isEmpty()).thenReturn(true);

        // Act & Assert
        try {
            photoService.uploadPhoto(multipartFile);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException e) {
            fail("Unexpected IOException");
        }
    }

    @Test
    void uploadPhoto_shouldThrowWhenFileExceedsMaxSize() {
        // Arrange
        ReflectionTestUtils.setField(photoService, "maxFileSize", 1024L * 1024L); // 1MB
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(2L * 1024L * 1024L); // 2MB

        // Act & Assert
        try {
            photoService.uploadPhoto(multipartFile);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException e) {
            fail("Unexpected IOException");
        }
    }

    @Test
    void uploadPhoto_shouldThrowWhenMimeTypeNotAllowed() {
        // Arrange
        ReflectionTestUtils.setField(photoService, "maxFileSize", 10L * 1024L * 1024L);
        ReflectionTestUtils.setField(photoService, "allowedMimeTypes", 
                new String[]{"image/jpeg", "image/png"});
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(100L);
        when(multipartFile.getContentType()).thenReturn("application/pdf");

        // Act & Assert
        try {
            photoService.uploadPhoto(multipartFile);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException e) {
            fail("Unexpected IOException");
        }
    }

    @Test
    void uploadPhoto_shouldAcceptValidMimeType() throws IOException {
        // Arrange
        ReflectionTestUtils.setField(photoService, "maxFileSize", 10L * 1024L * 1024L);
        ReflectionTestUtils.setField(photoService, "allowedMimeTypes", 
                new String[]{"image/jpeg", "image/png"});
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn(TEST_ORIGINAL_NAME);
        when(multipartFile.getSize()).thenReturn(TEST_SIZE);
        when(multipartFile.getContentType()).thenReturn(TEST_MIME_TYPE);
        when(fileStorageService.store(multipartFile)).thenReturn(TEST_STORED_PATH);
        when(fileStorageService.load(TEST_STORED_PATH)).thenReturn(Path.of(TEST_STORED_PATH));

        // Act & Assert
        PhotoDto result = photoService.uploadPhoto(multipartFile);
        assertNotNull(result);
    }
}
