package org.acs.stuco.backend.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.acs.stuco.backend.product.Product;
import org.acs.stuco.backend.user.User;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    
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
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime paidAt;

    @ManyToOne
    private User assignedRep;

    @Lob
    private String instructions;

    private String productName;

    @Column(precision = 19, scale = 4)
    private BigDecimal productPrice;

    
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


