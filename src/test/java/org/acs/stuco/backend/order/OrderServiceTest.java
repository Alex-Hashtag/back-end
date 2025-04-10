package org.acs.stuco.backend.order;

import org.acs.stuco.backend.exceptions.InsufficientStockException;
import org.acs.stuco.backend.exceptions.OrderNotFoundException;
import org.acs.stuco.backend.order.archive.ArchivedOrder;
import org.acs.stuco.backend.order.archive.ArchivedOrderRepository;
import org.acs.stuco.backend.product.Product;
import org.acs.stuco.backend.product.ProductService;
import org.acs.stuco.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class OrderServiceTest
{

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ArchivedOrderRepository archivedOrderRepository;

    @Mock
    private ProductService productService;

    private OrderService orderService;

    private Order sampleOrder;
    private Product sampleProduct;
    private User sampleUser;

    @BeforeEach
    void setUp()
    {
        orderService = new OrderService(orderRepository, archivedOrderRepository, productService);

        // Setup sample data
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setEmail("test@example.com");

        sampleProduct = new Product();
        sampleProduct.setId(1L);
        sampleProduct.setName("Test Product");
        sampleProduct.setPrice(BigDecimal.valueOf(10.99));
        sampleProduct.setAvailable(50);

        sampleOrder = new Order();
        sampleOrder.setId(1L);
        sampleOrder.setBuyer(sampleUser);
        sampleOrder.setProduct(sampleProduct);
        sampleOrder.setQuantity(5);
        sampleOrder.setStatus(OrderStatus.PENDING);
        sampleOrder.setPaymentType(PaymentType.CASH);
        sampleOrder.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void createOrder_WithValidProduct_ShouldSaveOrderAndReduceStock()
    {
        // Arrange
        when(productService.getProductById(sampleProduct.getId())).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);

        // Act
        Order result = orderService.createOrder(sampleOrder);

        // Assert
        assertNotNull(result);
        assertEquals(sampleOrder.getId(), result.getId());
        assertEquals(sampleProduct.getName(), result.getProductName());
        assertEquals(sampleProduct.getPrice(), result.getProductPrice());

        verify(productService).reduceStock(sampleProduct.getId(), sampleOrder.getQuantity());
        verify(orderRepository).save(sampleOrder);
    }

    @Test
    void createOrder_WithInsufficientStock_ShouldThrowException()
    {
        // Arrange
        Product lowStockProduct = new Product();
        lowStockProduct.setId(2L);
        lowStockProduct.setName("Low Stock Product");
        lowStockProduct.setPrice(BigDecimal.valueOf(10.99));
        lowStockProduct.setAvailable(2);  // Less than order quantity

        Order order = new Order();
        order.setProduct(lowStockProduct);
        order.setQuantity(5);  // More than available

        when(productService.getProductById(lowStockProduct.getId())).thenReturn(Optional.of(lowStockProduct));

        // Act & Assert
        assertThrows(InsufficientStockException.class, () ->
        {
            orderService.createOrder(order);
        });

        verify(productService, never()).reduceStock(anyLong(), anyInt());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_WithUnlimitedStockProduct_ShouldNotReduceStock()
    {
        // Arrange
        Product unlimitedProduct = new Product();
        unlimitedProduct.setId(3L);
        unlimitedProduct.setName("Unlimited Product");
        unlimitedProduct.setPrice(BigDecimal.valueOf(5.99));
        unlimitedProduct.setAvailable(-1);  // Unlimited stock

        Order order = new Order();
        order.setProduct(unlimitedProduct);
        order.setQuantity(100);
        order.setBuyer(sampleUser);
        order.setPaymentType(PaymentType.PREPAID);

        when(productService.getProductById(unlimitedProduct.getId())).thenReturn(Optional.of(unlimitedProduct));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // Act
        Order result = orderService.createOrder(order);

        // Assert
        assertNotNull(result);
        verify(productService, never()).reduceStock(anyLong(), anyInt());
        verify(orderRepository).save(order);
    }

    @Test
    void createOrder_WithNonExistentProduct_ButWithProductDetails_ShouldWork()
    {
        // Arrange
        Order orderWithoutProduct = new Order();
        orderWithoutProduct.setProduct(new Product());
        orderWithoutProduct.getProduct().setId(999L);  // Non-existent product ID
        orderWithoutProduct.setProductName("Custom Product");
        orderWithoutProduct.setProductPrice(BigDecimal.valueOf(15.99));
        orderWithoutProduct.setQuantity(2);
        orderWithoutProduct.setBuyer(sampleUser);
        orderWithoutProduct.setPaymentType(PaymentType.CASH);

        when(productService.getProductById(999L)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = orderService.createOrder(orderWithoutProduct);

        // Assert
        assertNotNull(result);
        assertNull(result.getProduct());
        assertEquals("Custom Product", result.getProductName());
        assertEquals(BigDecimal.valueOf(15.99), result.getProductPrice());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_WithNonExistentProductAndNoDetails_ShouldThrowException()
    {
        // Arrange
        Order invalidOrder = new Order();
        invalidOrder.setProduct(new Product());
        invalidOrder.getProduct().setId(999L);  // Non-existent product ID
        invalidOrder.setQuantity(2);
        invalidOrder.setBuyer(sampleUser);
        invalidOrder.setPaymentType(PaymentType.CASH);

        when(productService.getProductById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
        {
            orderService.createOrder(invalidOrder);
        });

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_ShouldUpdateAndReturnOrder()
    {
        // Arrange
        Long orderId = 1L;
        OrderStatus newStatus = OrderStatus.PAID;

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.updateStatus(anyLong(), any(OrderStatus.class))).thenReturn(1);

        // Act
        Order result = orderService.updateOrderStatus(orderId, newStatus);

        // Assert
        assertNotNull(result);
        verify(orderRepository).updateStatus(orderId, newStatus);
        verify(orderRepository).findById(orderId);
    }

    @Test
    void updateOrderStatus_WhenOrderNotFound_ShouldThrowException()
    {
        // Arrange
        Long orderId = 999L;
        OrderStatus newStatus = OrderStatus.PAID;

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        when(orderRepository.updateStatus(anyLong(), any(OrderStatus.class))).thenReturn(0);

        // Act & Assert
        assertThrows(OrderNotFoundException.class, () ->
        {
            orderService.updateOrderStatus(orderId, newStatus);
        });
    }

    @Test
    void getUserOrders_ShouldReturnUserOrders()
    {
        // Arrange
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        List<Order> orders = new ArrayList<>();
        orders.add(sampleOrder);
        Page<Order> orderPage = new PageImpl<>(orders, pageable, orders.size());

        when(orderRepository.findByBuyerId(userId, pageable)).thenReturn(orderPage);

        // Act
        Page<Order> result = orderService.getUserOrders(userId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(sampleOrder, result.getContent().get(0));
    }

    @Test
    void getAllOrders_ShouldReturnAllOrders()
    {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Order> orders = new ArrayList<>();
        orders.add(sampleOrder);
        Page<Order> orderPage = new PageImpl<>(orders, pageable, orders.size());

        when(orderRepository.findAll(pageable)).thenReturn(orderPage);

        // Act
        Page<Order> result = orderService.getAllOrders(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(sampleOrder, result.getContent().get(0));
    }

    @Test
    void getOrdersByStatus_ShouldReturnFilteredOrders()
    {
        // Arrange
        OrderStatus status = OrderStatus.PENDING;
        Pageable pageable = PageRequest.of(0, 10);
        List<Order> orders = new ArrayList<>();
        orders.add(sampleOrder);
        Page<Order> orderPage = new PageImpl<>(orders, pageable, orders.size());

        when(orderRepository.findByStatus(status, pageable)).thenReturn(orderPage);

        // Act
        Page<Order> result = orderService.getOrdersByStatus(status, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(status, result.getContent().get(0).getStatus());
    }

    @Test
    void getAssignedOrders_ShouldReturnOrdersAssignedToRep()
    {
        // Arrange
        Long repId = 2L;
        Pageable pageable = PageRequest.of(0, 10);

        User rep = new User();
        rep.setId(repId);

        Order assignedOrder = new Order();
        assignedOrder.setId(2L);
        assignedOrder.setAssignedRep(rep);

        List<Order> orders = new ArrayList<>();
        orders.add(assignedOrder);
        Page<Order> orderPage = new PageImpl<>(orders, pageable, orders.size());

        when(orderRepository.findByAssignedRepId(repId, pageable)).thenReturn(orderPage);

        // Act
        Page<Order> result = orderService.getAssignedOrders(repId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void getOrderStatistics_ShouldReturnStatistics()
    {
        // Arrange
        Object[] statisticsData = new Object[]{10L, 25, BigDecimal.valueOf(300.50)};
        when(orderRepository.getOrderStatistics()).thenReturn(statisticsData);

        // Act
        Object[] result = orderService.getOrderStatistics();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(10L, result[0]);  // Count
        assertEquals(25, result[1]);   // Sum of quantities
        assertEquals(BigDecimal.valueOf(300.50), result[2]); // Sum of prices
    }

    @Test
    void archiveDeliveredOrders_ShouldArchiveOldOrders()
    {
        // Arrange
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<Order> oldOrders = new ArrayList<>();
        oldOrders.add(sampleOrder);

        when(orderRepository.findDeliveredOrdersBefore(any(LocalDateTime.class))).thenReturn(oldOrders);
        when(archivedOrderRepository.save(any(ArchivedOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        orderService.archiveDeliveredOrders();

        // Assert
        verify(orderRepository).findDeliveredOrdersBefore(any(LocalDateTime.class));
        verify(archivedOrderRepository).save(any(ArchivedOrder.class));
        verify(orderRepository).delete(sampleOrder);
    }

    @Test
    void convertToArchivedOrder_ShouldMapAllFields()
    {
        // Arrange
        Order order = sampleOrder;

        // Act
        ArchivedOrder result = orderService.convertToArchivedOrder(order);

        // Assert
        assertNotNull(result);
        assertEquals(order.getId(), result.getId());
        assertEquals(order.getProduct(), result.getProduct());
        assertEquals(order.getBuyer(), result.getBuyer());
        assertEquals(order.getQuantity(), result.getQuantity());
        assertEquals(order.getStatus(), result.getStatus());
        assertEquals(order.getPaymentType(), result.getPaymentType());
        assertEquals(order.getCreatedAt(), result.getCreatedAt());
        assertEquals(order.getPaidAt(), result.getPaidAt());
        assertEquals(order.getAssignedRep(), result.getAssignedRep());
        assertEquals(order.getInstructions(), result.getInstructions());
        assertEquals(order.getProductName(), result.getProductName());
        assertEquals(order.getProductPrice(), result.getProductPrice());
    }

    @Test
    void getArchivedOrders_ShouldReturnArchivedOrders()
    {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        ArchivedOrder archivedOrder = new ArchivedOrder();
        archivedOrder.setId(1L);
        List<ArchivedOrder> orders = new ArrayList<>();
        orders.add(archivedOrder);
        Page<ArchivedOrder> orderPage = new PageImpl<>(orders, pageable, orders.size());

        when(archivedOrderRepository.findAll(pageable)).thenReturn(orderPage);

        // Act
        Page<ArchivedOrder> result = orderService.getArchivedOrders(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(archivedOrder, result.getContent().get(0));
    }
}