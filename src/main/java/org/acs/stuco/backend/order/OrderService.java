package org.acs.stuco.backend.order;

import org.acs.stuco.backend.exceptions.InsufficientStockException;
import org.acs.stuco.backend.exceptions.InvalidOperationException;
import org.acs.stuco.backend.exceptions.OrderNotFoundException;
import org.acs.stuco.backend.order.archive.ArchivedOrder;
import org.acs.stuco.backend.order.archive.ArchivedOrderRepository;
import org.acs.stuco.backend.product.Product;
import org.acs.stuco.backend.product.ProductService;
import org.acs.stuco.backend.user.User;
import org.acs.stuco.backend.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/// # Order Service
/// 
/// Service class that implements the core business logic for order management in the Student Council website.
/// This service handles order creation, status updates, and archiving of old orders.
/// 
/// ## Key Features
/// 
/// - Order creation with product stock validation
/// - Order status management with business rule enforcement
/// - Balance tracking for representatives who collect payments
/// - Archiving of old delivered orders
/// 
/// ## Order Status Flow
/// 
/// The order status follows this progression:
/// 1. PENDING (initial state)
/// 2. PAID (after payment is received)
/// 3. DELIVERED (after order is delivered to customer)
/// 
/// Orders can also be CANCELLED from any status.
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ArchivedOrderRepository archivedOrderRepository;
    private final ProductService productService;
    private final UserService userService;

    public OrderService(OrderRepository orderRepository,
                        ArchivedOrderRepository archivedOrderRepository,
                        ProductService productService,
                        UserService userService) {
        this.orderRepository = orderRepository;
        this.archivedOrderRepository = archivedOrderRepository;
        this.productService = productService;
        this.userService = userService;
    }

    /// Creates a new order with product validation and stock management.
    /// 
    /// This method handles:
    /// - Validating product availability
    /// - Reducing product stock when an order is placed
    /// - Setting product details on the order
    /// - Handling custom products without a product reference
    /// 
    /// @param order - The order to create with product and quantity information
    /// @return The saved order with generated ID
    /// @throws InsufficientStockException if the product doesn't have enough stock
    /// @throws IllegalArgumentException if product information is missing
    @Transactional
    public Order createOrder(Order order) {
        Product providedProduct = order.getProduct();

        if (providedProduct != null && providedProduct.getId() != null) {
            Optional<Product> productOpt = productService.getProductById(providedProduct.getId());
            if (productOpt.isPresent()) {
                Product product = productOpt.get();

                if (product.getAvailable() != -1 && product.getAvailable() < order.getQuantity()) {
                    throw new InsufficientStockException();
                }

                if (product.getAvailable() > 0) {
                    productService.reduceStock(product.getId(), order.getQuantity());
                    if (product.getAvailable() == 0) {
                        productService.deleteOutOfStockProducts();
                    }
                }

                order.setProductName(product.getName());
                order.setProductPrice(product.getPrice());
                order.setProduct(product);
            } else {
                if (order.getProductName() == null || order.getProductPrice() == null) {
                    throw new IllegalArgumentException("Product not found and no product details provided");
                }
                order.setProduct(null);
            }
        } else {
            if (order.getProductName() == null || order.getProductPrice() == null) {
                throw new IllegalArgumentException("Either a product reference or custom product details must be provided");
            }
        }

        return orderRepository.save(order);
    }

    /// Updates the status of an order with business rule enforcement.
    /// 
    /// This method implements the core business logic for order status updates:
    /// 1. Checks if another user already has the order assigned
    /// 2. Validates that the status change follows the proper progression
    /// 3. Assigns the user to the order if not already assigned
    /// 4. Updates timestamps based on status changes
    /// 5. Updates the user's balance when an order is marked as delivered
    /// 
    /// ## Status Progression Rules
    /// 
    /// - Orders must follow the sequence: PENDING → PAID → DELIVERED
    /// - Cannot skip statuses (e.g., PENDING directly to DELIVERED)
    /// - Cannot downgrade status (e.g., DELIVERED back to PAID)
    /// - Orders can be CANCELLED from any status
    /// 
    /// @param id - The ID of the order to update
    /// @param newStatus - The new status to set
    /// @param user - The user performing the status update
    /// @return The updated order
    /// @throws OrderNotFoundException if the order is not found
    /// @throws InvalidOperationException if the operation violates business rules
    @Transactional
    public Order updateOrderStatus(Long id, OrderStatus newStatus, User user) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        
        // Check if another user has already been assigned to this order
        if (order.getAssignedRep() != null && !order.getAssignedRep().getId().equals(user.getId())) {
            throw new InvalidOperationException("This order is already assigned to another representative");
        }
        
        // Check current status and validate status progression
        OrderStatus currentStatus = order.getStatus();
        validateStatusProgression(currentStatus, newStatus);
        
        // Assign the user to the order if not already assigned
        if (order.getAssignedRep() == null) {
            order.setAssignedRep(user);
        }
        
        // Update status
        order.setStatus(newStatus);
        
        // Set paidAt timestamp if status is changing to PAID
        if (newStatus == OrderStatus.PAID && currentStatus != OrderStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
        }
        
        // Calculate and update user's balance if order is being delivered
        if (newStatus == OrderStatus.DELIVERED && currentStatus != OrderStatus.DELIVERED) {
            BigDecimal orderValue = order.getTotalPrice();
            userService.incrementCollectedBalance(user.getId(), orderValue);
        }
        
        return orderRepository.save(order);
    }
    
    /// Validates that a status change follows the allowed progression rules.
    /// 
    /// @param currentStatus - The current status of the order
    /// @param newStatus - The requested new status
    /// @throws InvalidOperationException if the status progression is invalid
    private void validateStatusProgression(OrderStatus currentStatus, OrderStatus newStatus) {
        // Allow cancellation from any status
        if (newStatus == OrderStatus.CANCELLED) {
            return;
        }
        
        // Prevent status downgrade (except for cancellation which is handled above)
        if (getStatusRank(newStatus) < getStatusRank(currentStatus)) {
            throw new InvalidOperationException("Cannot downgrade order status from " + 
                                                currentStatus + " to " + newStatus);
        }
        
        // Prevent skipping status (e.g., PENDING directly to DELIVERED)
        if (newStatus == OrderStatus.DELIVERED && currentStatus == OrderStatus.PENDING) {
            throw new InvalidOperationException("Cannot change status directly from PENDING to DELIVERED");
        }
    }
    
    /// Returns a numeric rank for each status to simplify status progression checks.
    /// 
    /// @param status - The order status to get the rank for
    /// @return A numeric rank (0 for CANCELLED, 1-3 for normal progression)
    private int getStatusRank(OrderStatus status) {
        return switch (status) {
            case PENDING -> 1;
            case PAID -> 2;
            case DELIVERED -> 3;
            case CANCELLED -> 0; // Special case, can be set from any status
        };
    }

    /// Retrieves all orders for a specific user.
    /// 
    /// @param userId - The ID of the user
    /// @param pageable - Pagination parameters
    /// @return A paginated list of the user's orders
    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByBuyerId(userId, pageable);
    }

    /// Retrieves all orders in the system.
    /// 
    /// @param pageable - Pagination parameters
    /// @return A paginated list of all orders
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    /// Retrieves orders with a specific status.
    /// 
    /// @param status - The status to filter by
    /// @param pageable - Pagination parameters
    /// @return A paginated list of orders with the specified status
    public Page<Order> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable);
    }

    /// Retrieves orders assigned to a specific representative.
    /// 
    /// @param repId - The ID of the representative
    /// @param pageable - Pagination parameters
    /// @return A paginated list of orders assigned to the representative
    public Page<Order> getAssignedOrders(Long repId, Pageable pageable) {
        return orderRepository.findByAssignedRepId(repId, pageable);
    }

    /// Retrieves order statistics.
    /// 
    /// Returns an array containing:
    /// - [0]: Total count of delivered orders
    /// - [1]: Sum of quantities of all delivered orders
    /// - [2]: Total revenue from all delivered orders
    /// 
    /// @return Order statistics as an array of objects
    public Object[] getOrderStatistics() {
        return orderRepository.getOrderStatistics();
    }

    /// Archives delivered orders that are older than 30 days.
    /// 
    /// This method moves old delivered orders to an archive table to
    /// keep the main orders table efficient.
    @Transactional
    public void archiveDeliveredOrders() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<Order> ordersToArchive = orderRepository.findDeliveredOrdersBefore(cutoffDate);
        for (Order order : ordersToArchive) {
            ArchivedOrder archivedOrder = convertToArchivedOrder(order);
            archivedOrderRepository.save(archivedOrder);
            orderRepository.delete(order);
        }
        ordersToArchive = orderRepository.findCancledOrdersBefore(cutoffDate);
        for (Order order : ordersToArchive) {
            ArchivedOrder archivedOrder = convertToArchivedOrder(order);
            archivedOrderRepository.save(archivedOrder);
            orderRepository.delete(order);
        }
    }

    /// Converts an Order entity to an ArchivedOrder entity.
    /// 
    /// @param order - The order to convert
    /// @return The archived order with all data copied from the original
    public ArchivedOrder convertToArchivedOrder(Order order) {
        ArchivedOrder archivedOrder = new ArchivedOrder();
        archivedOrder.setId(order.getId());
        archivedOrder.setProduct(order.getProduct());
        archivedOrder.setBuyer(order.getBuyer());
        archivedOrder.setQuantity(order.getQuantity());
        archivedOrder.setStatus(order.getStatus());
        archivedOrder.setPaymentType(order.getPaymentType());
        archivedOrder.setCreatedAt(order.getCreatedAt());
        archivedOrder.setPaidAt(order.getPaidAt());
        archivedOrder.setAssignedRep(order.getAssignedRep());
        archivedOrder.setInstructions(order.getInstructions());
        archivedOrder.setProductName(order.getProductName());
        archivedOrder.setProductPrice(order.getProductPrice());

        return archivedOrder;
    }

    /// Retrieves archived orders.
    /// 
    /// @param pageable - Pagination parameters
    /// @return A paginated list of archived orders
    public Page<ArchivedOrder> getArchivedOrders(Pageable pageable) {
        return archivedOrderRepository.findAll(pageable);
    }
}
