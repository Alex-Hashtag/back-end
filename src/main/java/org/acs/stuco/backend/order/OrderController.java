package org.acs.stuco.backend.order;

import org.acs.stuco.backend.order.archive.ArchivedOrder;
import org.acs.stuco.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


/// # Order Controller
///
/// REST controller that handles all order-related operations in the Student Council website.
/// This controller provides endpoints for creating, retrieving, and managing orders.
///
/// ## Security
///
/// - Creating orders: Available to all authenticated users
/// - Viewing own orders: Available to all authenticated users
/// - Admin operations: Restricted to users with STUCO or ADMIN roles
/// - Order status updates: Available to REP, STUCO, and ADMIN roles
///
/// ## Order Status Flow
///
/// The order status follows this progression:
/// 1. PENDING (initial state)
/// 2. PAID (after payment is received)
/// 3. DELIVERED (after order is delivered to customer)
///
/// Orders can also be CANCELLED from any status.
@RestController
@RequestMapping("/api/orders")
public class OrderController
{

    private final OrderService orderService;

    public OrderController(OrderService orderService)
    {
        this.orderService = orderService;
    }

    /// Creates a new order for the authenticated user.
    ///
    /// This endpoint automatically sets the authenticated user as the buyer of the order.
    ///
    /// @param user  - The authenticated user creating the order
    /// @param order - The order details to create
    /// @return The created order with a generated ID
    @PostMapping
    public ResponseEntity<Order> createOrder(
            @AuthenticationPrincipal User user,
            @RequestBody Order order)
    {
        order.setBuyer(user);
        return ResponseEntity.ok(orderService.createOrder(order));
    }

    /// Retrieves all orders placed by the authenticated user.
    ///
    /// @param user     - The authenticated user
    /// @param pageable - Pagination parameters (page, size, sort)
    /// @return A paginated list of the user's orders
    @GetMapping
    public ResponseEntity<Page<Order>> getUserOrders(
            @AuthenticationPrincipal User user,
            Pageable pageable)
    {
        return ResponseEntity.ok(orderService.getUserOrders(user.getId(), pageable));
    }

    /// Retrieves all orders in the system (admin access only).
    ///
    /// This endpoint is restricted to users with STUCO or ADMIN roles.
    ///
    /// @param pageable - Pagination parameters (page, size, sort)
    /// @return A paginated list of all orders
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('STUCO', 'ADMIN')")
    public ResponseEntity<Page<Order>> getAllOrders(Pageable pageable)
    {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    /// Updates the status of an order.
    ///
    /// This endpoint performs several validations:
    /// 1. Checks if another user already has the order assigned
    /// 2. Validates that the status change follows the proper progression
    /// 3. Prevents status downgrades (except to CANCELLED)
    /// 4. Updates the executing user's balance when an order is marked as DELIVERED
    ///
    /// @param user   - The authenticated user performing the update
    /// @param id     - The ID of the order to update
    /// @param status - The new status to set
    /// @return The updated order
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('REP', 'STUCO', 'ADMIN')")
    public ResponseEntity<Order> updateOrderStatus(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam OrderStatus status)
    {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, status, user));
    }

    /// Retrieves order statistics (admin access only).
    ///
    /// Returns an array containing:
    /// - [0]: Total count of delivered orders
    /// - [1]: Sum of quantities of all delivered orders
    /// - [2]: Total revenue from all delivered orders
    ///
    /// @return Order statistics as an array of objects
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('STUCO', 'ADMIN')")
    public ResponseEntity<Object[]> getOrderStatistics()
    {
        return ResponseEntity.ok(orderService.getOrderStatistics());
    }

    /// Retrieves orders assigned to the authenticated user.
    ///
    /// This endpoint is primarily used by class representatives to view
    /// the orders they are responsible for handling.
    ///
    /// @param rep      - The authenticated user (representative)
    /// @param pageable - Pagination parameters (page, size, sort)
    /// @return A paginated list of orders assigned to the user
    @GetMapping("/assigned")
    @PreAuthorize("hasAnyRole('REP', 'STUCO', 'ADMIN')")
    public ResponseEntity<Page<Order>> getAssignedOrders(
            @AuthenticationPrincipal User rep,
            Pageable pageable)
    {
        return ResponseEntity.ok(orderService.getAssignedOrders(rep.getId(), pageable));
    }

    /// Retrieves archived orders (admin access only).
    ///
    /// Archived orders are older delivered orders that have been moved
    /// to a separate storage to keep the active orders table efficient.
    ///
    /// @param pageable - Pagination parameters (page, size, sort)
    /// @return A paginated list of archived orders
    @GetMapping("/archived")
    @PreAuthorize("hasAnyRole('STUCO', 'ADMIN')")
    public ResponseEntity<Page<ArchivedOrder>> getArchivedOrders(Pageable pageable)
    {
        return ResponseEntity.ok(orderService.getArchivedOrders(pageable));
    }
}
