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

    // GET /api/products - List available products (available != 0) with pagination
    @GetMapping
    public ResponseEntity<Page<Product>> listAvailableProducts(Pageable pageable)
    {
        return ResponseEntity.ok(productService.getAvailableProducts(pageable));
    }

    // GET /api/products/all - List all products (admin) with pagination
    @GetMapping("/all")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Page<Product>> listAllProducts(Pageable pageable)
    {
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    // GET /api/products/{id} - Get product detail if available
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id)
    {
        return productService.getAvailableProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/products - Create new product (StuCo+ only)
    @PostMapping
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Product> createProduct(
            @AuthenticationPrincipal User user,
            @RequestBody Product product)
    {
        product.setCreatedBy(user.getId());
        return ResponseEntity.ok(productService.createProduct(product));
    }

    // PUT /api/products/{id} - Full product update (StuCo+ only)
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
        // Convert the JSON string to a Product object.
        Product updatedProduct = objectMapper.readValue(productJson, Product.class);
        // If a new image file is provided, upload it and update the imageUrl.
        if (file != null && !file.isEmpty())
        {
            String imageUrl = productService.getUploadService().upload(file);
            updatedProduct.setImageUrl(imageUrl);
        }
        // Update the product using your existing service method.
        return ResponseEntity.ok(productService.updateProduct(id, updatedProduct));
    }

    // PATCH /api/products/{id} - Partial product update (StuCo+ only)
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Product> patchProduct(
            @PathVariable Long id,
            @RequestBody Product partialProduct)
    {
        return ResponseEntity.ok(productService.patchProduct(id, partialProduct));
    }

    // DELETE /api/products/{id} - Delete product (StuCo+ only)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id)
    {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // POST /api/products/cleanup - Delete out-of-stock products (StuCo+ only)
    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Void> cleanUpOutOfStock()
    {
        productService.deleteOutOfStockProducts();
        return ResponseEntity.noContent().build();
    }

    // POST /api/products/{id}/upload-image - Upload product image (StuCo+ only)
    @PostMapping(value = "/{id}/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Product> uploadProductImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file)
    {
        return ResponseEntity.ok(productService.uploadProductImage(id, file));
    }

    // POST /api/products/{id}/reduce-stock - Reduce product stock
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

