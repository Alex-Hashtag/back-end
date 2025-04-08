package org.acs.stuco.backend.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Entity
@Table(name = "products")
@Getter
@Setter
public class Product
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    /**
     * Available quantity:
     * > 0: limited stock
     * 0: sold out (should be auto-deleted)
     * -1: unlimited stock
     */
    @Column(nullable = false)
    private Integer available;

    private String imageUrl;

    // ID of the user who created the product
    private Long createdBy;
}
