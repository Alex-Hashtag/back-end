package org.acs.stuco.backend.order;

import org.acs.stuco.backend.order.archive.ArchivedOrder;
import org.acs.stuco.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/orders")
public class OrderController
{

    private final OrderService orderService;

    public OrderController(OrderService orderService)
    {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(
            @AuthenticationPrincipal User user,
            @RequestBody Order order)
    {
        order.setBuyer(user);
        return ResponseEntity.ok(orderService.createOrder(order));
    }

    @GetMapping
    public ResponseEntity<Page<Order>> getUserOrders(
            @AuthenticationPrincipal User user,
            Pageable pageable)
    {
        return ResponseEntity.ok(orderService.getUserOrders(user.getId(), pageable));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Page<Order>> getAllOrders(Pageable pageable)
    {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('REP') or hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status)
    {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, status));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Object[]> getOrderStatistics()
    {
        return ResponseEntity.ok(orderService.getOrderStatistics());
    }

    @GetMapping("/assigned")
    @PreAuthorize("hasRole('REP')")
    public ResponseEntity<Page<Order>> getAssignedOrders(
            @AuthenticationPrincipal User rep,
            Pageable pageable)
    {
        return ResponseEntity.ok(orderService.getAssignedOrders(rep.getId(), pageable));
    }

    @GetMapping("/archived")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Page<ArchivedOrder>> getArchivedOrders(Pageable pageable)
    {
        return ResponseEntity.ok(orderService.getArchivedOrders(pageable));
    }
}



