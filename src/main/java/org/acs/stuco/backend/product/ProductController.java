package org.acs.stuco.backend.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acs.stuco.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


@RestController
@RequestMapping("/api/products")
public class ProductController
{
    private final ProductService productService;
    private final ObjectMapper objectMapper;

    public ProductController(ProductService productService, ObjectMapper objectMapper)
    {
        this.productService = productService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<Page<Product>> listAvailableProducts(Pageable pageable)
    {
        return ResponseEntity.ok(productService.getAvailableProducts(pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<Page<Product>> listAllProducts(Pageable pageable)
    {
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id)
    {
        return productService.getAvailableProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Product> createProduct(
            @AuthenticationPrincipal User user,
            @RequestBody Product product)
    {
        product.setCreatedBy(user.getId());
        return ResponseEntity.ok(productService.createProduct(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @RequestBody Product product)
    {
        return ResponseEntity.ok(productService.updateProduct(id, product));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Product> updateProductWithImage(
            @PathVariable Long id,
            @RequestPart("product") String productJson,
            @RequestPart(value = "file", required = false) MultipartFile file) throws IOException
    {
        Product updatedProduct = objectMapper.readValue(productJson, Product.class);
        if (file != null && !file.isEmpty())
        {
            String imageUrl = productService.getUploadService().upload(file);
            updatedProduct.setImageUrl(imageUrl);
        }
        return ResponseEntity.ok(productService.updateProduct(id, updatedProduct));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Product> patchProduct(
            @PathVariable Long id,
            @RequestBody Product partialProduct)
    {
        return ResponseEntity.ok(productService.patchProduct(id, partialProduct));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id)
    {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Void> cleanUpOutOfStock()
    {
        productService.deleteOutOfStockProducts();
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Product> uploadProductImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file)
    {
        return ResponseEntity.ok(productService.uploadProductImage(id, file));
    }

    @PostMapping("/{id}/reduce-stock")
    public ResponseEntity<Void> reduceStock(
            @PathVariable Long id,
            @RequestParam int quantity)
    {
        int updated = productService.reduceStock(id, quantity);
        if (updated == 0)
        {
            throw new IllegalArgumentException("Failed to reduce stock - insufficient quantity available");
        }
        return ResponseEntity.ok().build();
    }
}


