package org.acs.stuco.backend.order.archive;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.acs.stuco.backend.order.OrderStatus;
import org.acs.stuco.backend.order.PaymentType;
import org.acs.stuco.backend.product.Product;
import org.acs.stuco.backend.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "archived_orders")
@Getter
@Setter
public class ArchivedOrder
{

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(nullable = true)  // was nullable = false
    private Product product;

    @ManyToOne
    @JoinColumn(nullable = false)
    private User buyer;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    @ManyToOne
    private User assignedRep;

    @Lob
    private String instructions;

    @Column
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


