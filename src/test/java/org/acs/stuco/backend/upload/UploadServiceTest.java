package org.acs.stuco.backend.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @InjectMocks
    private UploadService uploadService;

    private MultipartFile testFile;

    @BeforeEach
    void setUp() {
        // Set up the mock cloudinary and uploader
        when(cloudinary.uploader()).thenReturn(uploader);

        // Create a test MultipartFile
        testFile = new MockMultipartFile(
                "testFile",
                "test.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        // Inject the mocked cloudinary into the uploadService
        ReflectionTestUtils.setField(uploadService, "cloudinary", cloudinary);
    }

    @Test
    void upload_WithValidFile_ShouldReturnUrl() throws IOException {
        // Arrange
        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "https://cloudinary.com/test-image.jpg");

        when(uploader.upload(any(byte[].class), any())).thenReturn(uploadResult);

        // Act
        String result = uploadService.upload(testFile);

        // Assert
        assertEquals("https://cloudinary.com/test-image.jpg", result);
        verify(uploader).upload(any(byte[].class), any());
    }


    @Test
    void upload_WhenUploadFails_ShouldThrowException() throws IOException {
        // Arrange
        when(uploader.upload(any(byte[].class), any())).thenThrow(new IOException("Upload failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            uploadService.upload(testFile);
        });

        assertEquals("Could not store file. Please try again!", exception.getMessage());
        verify(uploader).upload(any(byte[].class), any());
    }

    @Test
    void delete_WithValidUrl_ShouldReturnTrue() throws IOException {
        // Arrange
        String imageUrl = "https://res.cloudinary.com/mycloud/image/upload/v1234567890/abc123.jpg";
        Map<String, Object> deleteResult = new HashMap<>();
        deleteResult.put("result", "ok");

        when(uploader.destroy(eq("abc123"), any())).thenReturn(deleteResult);

        // Act
        boolean result = uploadService.delete(imageUrl);

        // Assert
        assertTrue(result);
        verify(uploader).destroy(eq("abc123"), any());
    }

    @Test
    void delete_WithFailedDeletion_ShouldReturnFalse() throws IOException {
        // Arrange
        String imageUrl = "https://res.cloudinary.com/mycloud/image/upload/v1234567890/abc123.jpg";
        Map<String, Object> deleteResult = new HashMap<>();
        deleteResult.put("result", "not_found");

        when(uploader.destroy(eq("abc123"), any())).thenReturn(deleteResult);

        // Act
        boolean result = uploadService.delete(imageUrl);

        // Assert
        assertFalse(result);
        verify(uploader).destroy(eq("abc123"), any());
    }


    @Test
    void delete_WhenDeleteFails_ShouldThrowException() throws IOException {
        // Arrange
        String imageUrl = "https://res.cloudinary.com/mycloud/image/upload/v1234567890/abc123.jpg";
        when(uploader.destroy(eq("abc123"), any())).thenThrow(new IOException("Delete failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            uploadService.delete(imageUrl);
        });

        assertEquals("Failed to delete image: " + imageUrl, exception.getMessage());
        verify(uploader).destroy(eq("abc123"), any());
    }
}
