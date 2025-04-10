package org.acs.stuco.backend.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;


@Repository
public interface ProductRepository extends JpaRepository<Product, Long>
{

    Page<Product> findByAvailableGreaterThan(int minAvailable, Pageable pageable);

    Page<Product> findByAvailable(int exactAvailable, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.available != 0")
    Page<Product> findAvailableProducts(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.available != 0")
    Optional<Product> findAvailableById(Long id);

    Page<Product> findByCreatedBy(Long createdBy, Pageable pageable);

    Page<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Modifying
    @Query("UPDATE Product p SET p.available = p.available - :quantity WHERE p.id = :id AND p.available >= :quantity")
    int reduceStock(Long id, int quantity);

    @Modifying
    @Query("DELETE FROM Product p WHERE p.available = 0")
    void deleteOutOfStockProducts();

    @Query("SELECT MIN(p.price), MAX(p.price), AVG(p.price) FROM Product p WHERE p.available != 0")
    Object[] getPriceStatistics();

    @Query("SELECT p FROM Product p WHERE p.available = -1")
    Page<Product> findUnlimitedStockProducts(Pageable pageable);
}
