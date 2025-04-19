package org.acs.stuco.backend.product;

import org.acs.stuco.backend.exceptions.ProductNotFoundException;
import org.acs.stuco.backend.upload.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UploadService uploadService;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private Page<Product> productPage;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Test Product");
        testProduct.setDescription("Test Description");
        testProduct.setPrice(new BigDecimal("10.00"));
        testProduct.setAvailable(10);
        testProduct.setImageUrl("http://example.com/image.jpg");

        productPage = new PageImpl<>(List.of(testProduct));
    }

    @Test
    void getAvailableProducts_ShouldCallRepository() {
        // Arrange
        Pageable pageable = mock(Pageable.class);
        when(productRepository.findAvailableProducts(pageable)).thenReturn(productPage);

        // Act
        Page<Product> result = productService.getAvailableProducts(pageable);

        // Assert
        assertEquals(productPage, result);
        verify(productRepository).findAvailableProducts(pageable);
    }

    @Test
    void getAllProducts_ShouldCallRepository() {
        // Arrange
        Pageable pageable = mock(Pageable.class);
        when(productRepository.findAll(pageable)).thenReturn(productPage);

        // Act
        Page<Product> result = productService.getAllProducts(pageable);

        // Assert
        assertEquals(productPage, result);
        verify(productRepository).findAll(pageable);
    }

    @Test
    void getProductById_ShouldReturnOptionalProduct() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        // Act
        Optional<Product> result = productService.getProductById(1L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testProduct, result.get());
        verify(productRepository).findById(1L);
    }

    @Test
    void getAvailableProductById_ShouldReturnOptionalProduct() {
        // Arrange
        when(productRepository.findAvailableById(1L)).thenReturn(Optional.of(testProduct));

        // Act
        Optional<Product> result = productService.getAvailableProductById(1L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testProduct, result.get());
        verify(productRepository).findAvailableById(1L);
    }

    @Test
    void createProduct_WithValidProduct_ShouldSaveAndReturn() {
        // Arrange
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // Act
        Product result = productService.createProduct(testProduct);

        // Assert
        assertEquals(testProduct, result);
        verify(productRepository).save(testProduct);
    }

    @Test
    void createProduct_WithNegativeAvailable_ShouldThrowException() {
        // Arrange
        testProduct.setAvailable(-5); // -1 is allowed for unlimited, but less is not

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(testProduct);
        });

        assertEquals("Product quantity cannot be negative (except -1 for unlimited)", exception.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_WithNegativePrice_ShouldThrowException() {
        // Arrange
        testProduct.setPrice(new BigDecimal("-10.00"));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(testProduct);
        });

        assertEquals("Product price cannot be negative", exception.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_WhenProductExists_ShouldUpdateAndReturn() {
        // Arrange
        Product updatedProduct = new Product();
        updatedProduct.setName("Updated Name");
        updatedProduct.setDescription("Updated Description");
        updatedProduct.setPrice(new BigDecimal("20.00"));
        updatedProduct.setAvailable(5);
        updatedProduct.setImageUrl("http://example.com/updated.jpg");

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Product result = productService.updateProduct(1L, updatedProduct);

        // Assert
        assertEquals("Updated Name", result.getName());
        assertEquals("Updated Description", result.getDescription());
        assertEquals(new BigDecimal("20.00"), result.getPrice());
        assertEquals(5, result.getAvailable());
        assertEquals("http://example.com/updated.jpg", result.getImageUrl());
        verify(productRepository).save(testProduct);
    }

    @Test
    void updateProduct_WhenProductDoesNotExist_ShouldThrowException() {
        // Arrange
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ProductNotFoundException.class, () -> {
            productService.updateProduct(99L, testProduct);
        });
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void patchProduct_WhenProductExists_ShouldPartiallyUpdateAndReturn() {
        // Arrange
        Product partialUpdate = new Product();
        partialUpdate.setName("Patched Name");
        // Only updating the name, leaving other fields null

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Product result = productService.patchProduct(1L, partialUpdate);

        // Assert
        assertEquals("Patched Name", result.getName());
        // Other fields should remain unchanged
        assertEquals("Test Description", result.getDescription());
        assertEquals(new BigDecimal("10.00"), result.getPrice());
        assertEquals(10, result.getAvailable());
        assertEquals("http://example.com/image.jpg", result.getImageUrl());
        verify(productRepository).save(testProduct);
    }

    @Test
    void patchProduct_WhenProductDoesNotExist_ShouldThrowException() {
        // Arrange
        Product partialUpdate = new Product();
        partialUpdate.setName("Patched Name");

        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ProductNotFoundException.class, () -> {
            productService.patchProduct(99L, partialUpdate);
        });
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteProduct_WhenProductExistsWithImage_ShouldDeleteProductAndImage() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        doNothing().when(productRepository).delete(any(Product.class));

        // Act
        productService.deleteProduct(1L);

        // Assert
        verify(uploadService).delete(testProduct.getImageUrl());
        verify(productRepository).delete(testProduct);
    }

    @Test
    void deleteProduct_WhenProductExistsWithoutImage_ShouldOnlyDeleteProduct() {
        // Arrange
        testProduct.setImageUrl(null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        doNothing().when(productRepository).delete(any(Product.class));

        // Act
        productService.deleteProduct(1L);

        // Assert
        verify(uploadService, never()).delete(anyString());
        verify(productRepository).delete(testProduct);
    }

    @Test
    void deleteProduct_WhenProductDoesNotExist_ShouldThrowException() {
        // Arrange
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ProductNotFoundException.class, () -> {
            productService.deleteProduct(99L);
        });
        verify(uploadService, never()).delete(anyString());
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void deleteOutOfStockProducts_ShouldCallRepository() {
        // Arrange
        doNothing().when(productRepository).deleteOutOfStockProducts();

        // Act
        productService.deleteOutOfStockProducts();

        // Assert
        verify(productRepository).deleteOutOfStockProducts();
    }

    @Test
    void uploadProductImage_WhenProductExistsAndFileNotEmpty_ShouldUploadAndUpdate() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(uploadService.upload(file)).thenReturn("http://example.com/uploaded.jpg");
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Product result = productService.uploadProductImage(1L, file);

        // Assert
        assertEquals("http://example.com/uploaded.jpg", result.getImageUrl());
        verify(uploadService).upload(file);
        verify(productRepository).save(testProduct);
    }

    @Test
    void uploadProductImage_WhenFileIsEmpty_ShouldThrowException() {
        // Arrange
        MultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.uploadProductImage(1L, emptyFile);
        });

        assertEquals("File cannot be empty", exception.getMessage());
        verify(uploadService, never()).upload(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void uploadProductImage_WhenProductDoesNotExist_ShouldThrowException() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ProductNotFoundException.class, () -> {
            productService.uploadProductImage(99L, file);
        });
        verify(uploadService, never()).upload(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void reduceStock_ShouldCallRepository() {
        // Arrange
        when(productRepository.reduceStock(1L, 5)).thenReturn(1);

        // Act
        int result = productService.reduceStock(1L, 5);

        // Assert
        assertEquals(1, result);
        verify(productRepository).reduceStock(1L, 5);
    }
}
