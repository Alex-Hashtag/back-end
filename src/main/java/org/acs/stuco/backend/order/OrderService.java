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

/**
 * Service class for managing orders.
 */
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

    /**
     * Creates a new order.
     *
     * @param order The order to create
     * @return The created order
     */
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

    /**
     * Updates the status of an order and handles the business logic for assigning representatives
     * and updating balances.
     *
     * @param id The ID of the order to update
     * @param newStatus The new status to set
     * @param user The user performing the status update
     * @return The updated order
     * @throws OrderNotFoundException If the order is not found
     * @throws InvalidOperationException If the operation is not allowed
     */
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

    /**
     * Validates that the status change follows the allowed progression:
     * PENDING -> PAID -> DELIVERED or any status -> CANCELLED
     *
     * @param currentStatus The current status of the order
     * @param newStatus The requested new status
     * @throws InvalidOperationException If the status progression is invalid
     */
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

    /**
     * Returns a numeric rank for each status to simplify status progression checks
     */
    private int getStatusRank(OrderStatus status) {
        return switch (status) {
            case PENDING -> 1;
            case PAID -> 2;
            case DELIVERED -> 3;
            case CANCELLED -> 0; // Special case, can be set from any status
        };
    }

    /**
     * Retrieves the orders for a specific user.
     *
     * @param userId The ID of the user
     * @param pageable The pagination information
     * @return The orders for the user
     */
    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByBuyerId(userId, pageable);
    }

    /**
     * Retrieves all orders.
     *
     * @param pageable The pagination information
     * @return All orders
     */
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    /**
     * Retrieves the orders with a specific status.
     *
     * @param status The status to filter by
     * @param pageable The pagination information
     * @return The orders with the specified status
     */
    public Page<Order> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable);
    }

    /**
     * Retrieves the orders assigned to a specific representative.
     *
     * @param repId The ID of the representative
     * @param pageable The pagination information
     * @return The orders assigned to the representative
     */
    public Page<Order> getAssignedOrders(Long repId, Pageable pageable) {
        return orderRepository.findByAssignedRepId(repId, pageable);
    }

    /**
     * Retrieves the order statistics.
     *
     * @return The order statistics
     */
    public Object[] getOrderStatistics() {
        return orderRepository.getOrderStatistics();
    }

    /**
     * Archives delivered orders older than 30 days.
     */
    @Transactional
    public void archiveDeliveredOrders() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<Order> ordersToArchive = orderRepository.findDeliveredOrdersBefore(cutoffDate);
        for (Order order : ordersToArchive) {
            ArchivedOrder archivedOrder = convertToArchivedOrder(order);
            archivedOrderRepository.save(archivedOrder);
            orderRepository.delete(order);
        }
    }

    /**
     * Converts an order to an archived order.
     *
     * @param order The order to convert
     * @return The archived order
     */
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

    /**
     * Retrieves the archived orders.
     *
     * @param pageable The pagination information
     * @return The archived orders
     */
    public Page<ArchivedOrder> getArchivedOrders(Pageable pageable) {
        return archivedOrderRepository.findAll(pageable);
    }
}
