package org.acs.stuco.backend.product;

import jakarta.transaction.Transactional;
import lombok.Getter;
import org.acs.stuco.backend.exceptions.ProductNotFoundException;
import org.acs.stuco.backend.upload.UploadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Optional;


@Service
public class ProductService
{

    private final ProductRepository productRepository;
    @Getter
    private final UploadService uploadService;

    public ProductService(ProductRepository productRepository,
                          UploadService uploadService)
    {
        this.productRepository = productRepository;
        this.uploadService = uploadService;
    }

    @Transactional
    public Page<Product> getAvailableProducts(Pageable pageable)
    {
        return productRepository.findAvailableProducts(pageable);
    }

    @Transactional
    public Page<Product> getAllProducts(Pageable pageable)
    {
        return productRepository.findAll(pageable);
    }

    public Optional<Product> getProductById(Long id)
    {
        return productRepository.findById(id);
    }

    public Optional<Product> getAvailableProductById(Long id)
    {
        return productRepository.findAvailableById(id);
    }

    @Transactional
    public Product createProduct(Product product)
    {
        validateProduct(product);
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long id, Product updatedProduct)
    {
        validateProduct(updatedProduct);
        return productRepository.findById(id)
                .map(existingProduct ->
                {
                    existingProduct.setName(updatedProduct.getName());
                    existingProduct.setDescription(updatedProduct.getDescription());
                    existingProduct.setPrice(updatedProduct.getPrice());
                    existingProduct.setAvailable(updatedProduct.getAvailable());
                    existingProduct.setImageUrl(updatedProduct.getImageUrl());
                    return productRepository.save(existingProduct);
                })
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product patchProduct(Long id, Product partialProduct)
    {
        return productRepository.findById(id)
                .map(existingProduct ->
                {
                    if (partialProduct.getName() != null)
                    {
                        existingProduct.setName(partialProduct.getName());
                    }
                    if (partialProduct.getDescription() != null)
                    {
                        existingProduct.setDescription(partialProduct.getDescription());
                    }
                    if (partialProduct.getPrice() != null)
                    {
                        existingProduct.setPrice(partialProduct.getPrice());
                    }
                    if (partialProduct.getAvailable() != null)
                    {
                        existingProduct.setAvailable(partialProduct.getAvailable());
                    }
                    if (partialProduct.getImageUrl() != null)
                    {
                        existingProduct.setImageUrl(partialProduct.getImageUrl());
                    }
                    return productRepository.save(existingProduct);
                })
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public void deleteProduct(Long id)
    {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty())
        {
            uploadService.delete(product.getImageUrl());
        }
        productRepository.delete(product);
    }

    @Transactional
    public void deleteOutOfStockProducts()
    {
        productRepository.deleteOutOfStockProducts();
    }

    @Transactional
    public Product uploadProductImage(Long id, MultipartFile file)
    {
        if (file.isEmpty())
        {
            throw new IllegalArgumentException("File cannot be empty");
        }

        return productRepository.findById(id)
                .map(product ->
                {
                    String imageUrl = uploadService.upload(file);
                    product.setImageUrl(imageUrl);
                    return productRepository.save(product);
                })
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public int reduceStock(Long id, int quantity)
    {
        return productRepository.reduceStock(id, quantity);
    }

    private void validateProduct(Product product)
    {
        if (product.getAvailable() != null && product.getAvailable() < -1)
        {
            throw new IllegalArgumentException("Product quantity cannot be negative (except -1 for unlimited)");
        }
        if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new IllegalArgumentException("Product price cannot be negative");
        }
    }
}


