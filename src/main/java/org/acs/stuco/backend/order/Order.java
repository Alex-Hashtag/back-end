package org.acs.stuco.backend.order;

import jakarta.persistence.*;
import lombok.*;
import org.acs.stuco.backend.product.Product;
import org.acs.stuco.backend.user.User;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * Represents an order in the system.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The product associated with this order.
     */
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = true,
            foreignKey = @ForeignKey(name = "FK_orders_product"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Product product;

    @ManyToOne
    @JoinColumn(nullable = false)
    private User buyer;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime paidAt;

    @ManyToOne
    private User assignedRep;

    @Lob
    private String instructions;

    private String productName;

    @Column(precision = 19, scale = 4)
    private BigDecimal productPrice;

    /**
     * Calculates the total price of the order.
     *
     * @return the total price of the order
     */
    public BigDecimal getTotalPrice()
    {
        if (product != null)
        {
            return product.getPrice().multiply(BigDecimal.valueOf(quantity));
        }
        else if (productPrice != null)
        {
            return productPrice.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }
}
