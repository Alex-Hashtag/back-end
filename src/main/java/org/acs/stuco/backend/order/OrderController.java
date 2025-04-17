package org.acs.stuco.backend.order;

import org.acs.stuco.backend.order.archive.ArchivedOrder;
import org.acs.stuco.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for handling order-related operations.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order for the authenticated user.
     *
     * @param user The authenticated user
     * @param order The order to create
     * @return The created order
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(
            @AuthenticationPrincipal User user,
            @RequestBody Order order) {
        order.setBuyer(user);
        return ResponseEntity.ok(orderService.createOrder(order));
    }

    /**
     * Retrieves the orders for the authenticated user.
     *
     * @param user The authenticated user
     * @param pageable The pagination information
     * @return The orders for the user
     */
    @GetMapping
    public ResponseEntity<Page<Order>> getUserOrders(
            @AuthenticationPrincipal User user,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.getUserOrders(user.getId(), pageable));
    }

    /**
     * Retrieves all orders (admin access).
     *
     * @param pageable The pagination information
     * @return All orders
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('STUCO', 'ADMIN')")
    public ResponseEntity<Page<Order>> getAllOrders(Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    /**
     * Updates the status of an order.
     * This endpoint checks:
     * 1. If another user already has the order
     * 2. If the order's current status is valid for the requested change
     * 3. If the status change follows the progression (pending->paid->delivered)
     * 4. Adds the order value to the user's balance if delivered
     *
     * @param user The authenticated user performing the update
     * @param id The ID of the order to update
     * @param status The new status
     * @return The updated order
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('REP', 'STUCO', 'ADMIN')")
    public ResponseEntity<Order> updateOrderStatus(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, status, user));
    }

    /**
     * Retrieves order statistics.
     *
     * @return The order statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('STUCO', 'ADMIN')")
    public ResponseEntity<Object[]> getOrderStatistics() {
        return ResponseEntity.ok(orderService.getOrderStatistics());
    }

    /**
     * Retrieves the orders assigned to the authenticated user.
     *
     * @param rep The authenticated user (representative)
     * @param pageable The pagination information
     * @return The orders assigned to the user
     */
    @GetMapping("/assigned")
    @PreAuthorize("hasAnyRole('REP', 'STUCO', 'ADMIN')")
    public ResponseEntity<Page<Order>> getAssignedOrders(
            @AuthenticationPrincipal User rep,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.getAssignedOrders(rep.getId(), pageable));
    }

    /**
     * Retrieves archived orders.
     *
     * @param pageable The pagination information
     * @return The archived orders
     */
    @GetMapping("/archived")
    @PreAuthorize("hasAnyRole('STUCO', 'ADMIN')")
    public ResponseEntity<Page<ArchivedOrder>> getArchivedOrders(Pageable pageable) {
        return ResponseEntity.ok(orderService.getArchivedOrders(pageable));
    }
}
