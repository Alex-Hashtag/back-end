package org.acs.stuco.backend.order;

import org.acs.stuco.backend.exceptions.InsufficientStockException;
import org.acs.stuco.backend.exceptions.InvalidOperationException;
import org.acs.stuco.backend.exceptions.OrderNotFoundException;
import org.acs.stuco.backend.order.archive.ArchivedOrder;
import org.acs.stuco.backend.order.archive.ArchivedOrderRepository;
import org.acs.stuco.backend.product.Product;
import org.acs.stuco.backend.product.ProductService;
import org.acs.stuco.backend.user.Role;
import org.acs.stuco.backend.user.User;
import org.acs.stuco.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private ArchivedOrderRepository archivedOrderRepository;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private UserService userService;

    private Order testOrder;
    private Product testProduct;
    private User testUser;
    private User testBuyer;
    private final Long testOrderId = 1L;
    private final Long testProductId = 1L;
    private final Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        // Initialize test product
        testProduct = new Product();
        testProduct.setId(testProductId);
        testProduct.setName("Test Product");
        testProduct.setPrice(new BigDecimal("10.00"));
        testProduct.setAvailable(5);

        // Initialize test user (class rep)
        testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail("test@acsbg.org");
        testUser.setName("Test User");
        testUser.setRole(Role.CLASS_REP);
        
        // Initialize test buyer
        testBuyer = new User();
        testBuyer.setId(2L);
        testBuyer.setEmail("buyer@acsbg.org");
        testBuyer.setName("Buyer User");
        testBuyer.setRole(Role.USER);

        // Initialize test order using builder
        testOrder = Order.builder()
            .id(testOrderId)
            .product(testProduct)
            .buyer(testBuyer)
            .productName(testProduct.getName())
            .productPrice(testProduct.getPrice())
            .quantity(2)
            .status(OrderStatus.PENDING)
            .paymentType(PaymentType.CASH)
            .createdAt(LocalDateTime.now().minusDays(1))
            .build();
    }

    @Test
    void createOrder_WithExistingProduct_ShouldCreateOrderAndReduceStock() {
        // Arrange
        when(productService.getProductById(testProductId)).thenReturn(Optional.of(testProduct));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.createOrder(testOrder);

        // Assert
        assertNotNull(result);
        assertEquals(testOrderId, result.getId());
        assertEquals(testProduct.getName(), result.getProductName());
        assertEquals(testProduct.getPrice(), result.getProductPrice());
        
        verify(productService).getProductById(testProductId);
        verify(productService).reduceStock(testProductId, 2);
        verify(orderRepository).save(testOrder);
    }

    @Test
    void createOrder_WithInsufficientStock_ShouldThrowException() {
        // Arrange
        testProduct.setAvailable(1); // Only 1 available, but order quantity is 2
        when(productService.getProductById(testProductId)).thenReturn(Optional.of(testProduct));

        // Act & Assert
        assertThrows(InsufficientStockException.class, () -> {
            orderService.createOrder(testOrder);
        });
        
        verify(productService).getProductById(testProductId);
        verify(productService, never()).reduceStock(anyLong(), anyInt());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_WithUnlimitedStock_ShouldNotReduceStock() {
        // Arrange
        testProduct.setAvailable(-1); // -1 indicates unlimited stock
        when(productService.getProductById(testProductId)).thenReturn(Optional.of(testProduct));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.createOrder(testOrder);

        // Assert
        assertNotNull(result);
        verify(productService).getProductById(testProductId);
        verify(productService, never()).reduceStock(anyLong(), anyInt());
        verify(orderRepository).save(testOrder);
    }

    @Test
    void createOrder_WithCustomProduct_ShouldCreateOrderWithoutProductReference() {
        // Arrange
        Order customOrder = Order.builder()
            .id(testOrderId)
            .buyer(testBuyer)
            .productName("Custom Product")
            .productPrice(new BigDecimal("15.00"))
            .quantity(1)
            .status(OrderStatus.PENDING)
            .paymentType(PaymentType.CASH)
            .build();
        
        when(orderRepository.save(any(Order.class))).thenReturn(customOrder);

        // Act
        Order result = orderService.createOrder(customOrder);

        // Assert
        assertNotNull(result);
        assertEquals("Custom Product", result.getProductName());
        assertEquals(new BigDecimal("15.00"), result.getProductPrice());
        assertNull(result.getProduct());
        
        verify(productService, never()).getProductById(anyLong());
        verify(productService, never()).reduceStock(anyLong(), anyInt());
        verify(orderRepository).save(customOrder);
    }

    @Test
    void createOrder_WithMissingProductDetails_ShouldThrowException() {
        // Arrange
        Order invalidOrder = Order.builder()
            .buyer(testBuyer)
            .quantity(1)
            .paymentType(PaymentType.CASH)
            .build();
        // Missing both Product reference and productName/productPrice

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrder(invalidOrder);
        });
        
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_FromPendingToPaid_ShouldUpdateStatusAndSetPaidAt() {
        // Arrange
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = orderService.updateOrderStatus(testOrderId, OrderStatus.PAID, testUser);

        // Assert
        assertEquals(OrderStatus.PAID, result.getStatus());
        assertNotNull(result.getPaidAt());
        assertEquals(testUser, result.getAssignedRep());
        
        verify(orderRepository).findById(testOrderId);
        verify(orderRepository).save(testOrder);
        verify(userService, never()).incrementCollectedBalance(anyLong(), any(BigDecimal.class));
    }

    @Test
    void updateOrderStatus_FromPaidToDelivered_ShouldUpdateStatusAndIncrementBalance() {
        // Arrange
        testOrder.setStatus(OrderStatus.PAID);
        testOrder.setPaidAt(LocalDateTime.now().minusHours(1));
        testOrder.setAssignedRep(testUser);
        
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.incrementCollectedBalance(anyLong(), any(BigDecimal.class))).thenReturn(testUser);

        // Act
        Order result = orderService.updateOrderStatus(testOrderId, OrderStatus.DELIVERED, testUser);

        // Assert
        assertEquals(OrderStatus.DELIVERED, result.getStatus());
        assertEquals(testUser, result.getAssignedRep());
        
        verify(orderRepository).findById(testOrderId);
        verify(orderRepository).save(testOrder);
        verify(userService).incrementCollectedBalance(testUserId, testOrder.getTotalPrice());
    }

    @Test
    void updateOrderStatus_FromPendingToDelivered_ShouldThrowException() {
        // Arrange
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> {
            orderService.updateOrderStatus(testOrderId, OrderStatus.DELIVERED, testUser);
        });
        
        verify(orderRepository).findById(testOrderId);
        verify(orderRepository, never()).save(any(Order.class));
        verify(userService, never()).incrementCollectedBalance(anyLong(), any(BigDecimal.class));
    }

    @Test
    void updateOrderStatus_ToLowerStatus_ShouldThrowException() {
        // Arrange
        testOrder.setStatus(OrderStatus.PAID);
        testOrder.setAssignedRep(testUser);
        
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> {
            orderService.updateOrderStatus(testOrderId, OrderStatus.PENDING, testUser);
        });
        
        verify(orderRepository).findById(testOrderId);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_ToCancelled_ShouldWorkFromAnyStatus() {
        // Arrange
        testOrder.setStatus(OrderStatus.PAID);
        testOrder.setAssignedRep(testUser);
        
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = orderService.updateOrderStatus(testOrderId, OrderStatus.CANCELLED, testUser);

        // Assert
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        
        verify(orderRepository).findById(testOrderId);
        verify(orderRepository).save(testOrder);
    }

    @Test
    void updateOrderStatus_WithDifferentUser_ShouldThrowException() {
        // Arrange
        User anotherUser = new User();
        anotherUser.setId(2L);
        anotherUser.setEmail("another@acsbg.org");
        anotherUser.setRole(Role.CLASS_REP);
        
        testOrder.setAssignedRep(anotherUser);
        
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> {
            orderService.updateOrderStatus(testOrderId, OrderStatus.PAID, testUser);
        });
        
        verify(orderRepository).findById(testOrderId);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_WithNonExistentOrder_ShouldThrowException() {
        // Arrange
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.updateOrderStatus(testOrderId, OrderStatus.PAID, testUser);
        });
        
        verify(orderRepository).findById(testOrderId);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void getUserOrders_ShouldReturnPageOfUserOrders() {
        // Arrange
        Page<Order> mockPage = new PageImpl<>(List.of(testOrder));
        Pageable pageable = mock(Pageable.class);
        when(orderRepository.findByBuyerId(anyLong(), any(Pageable.class))).thenReturn(mockPage);

        // Act
        Page<Order> result = orderService.getUserOrders(testUserId, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals(testOrder, result.getContent().get(0));
        verify(orderRepository).findByBuyerId(testUserId, pageable);
    }

    @Test
    void getAllOrders_ShouldReturnPageOfOrders() {
        // Arrange
        Page<Order> mockPage = new PageImpl<>(List.of(testOrder));
        Pageable pageable = mock(Pageable.class);
        when(orderRepository.findAll(pageable)).thenReturn(mockPage);

        // Act
        Page<Order> result = orderService.getAllOrders(pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals(testOrder, result.getContent().get(0));
        verify(orderRepository).findAll(pageable);
    }

    @Test
    void getOrdersByStatus_ShouldReturnPageOfOrdersWithSpecifiedStatus() {
        // Arrange
        Page<Order> mockPage = new PageImpl<>(List.of(testOrder));
        Pageable pageable = mock(Pageable.class);
        OrderStatus status = OrderStatus.PENDING;
        
        when(orderRepository.findByStatus(status, pageable)).thenReturn(mockPage);

        // Act
        Page<Order> result = orderService.getOrdersByStatus(status, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals(testOrder, result.getContent().get(0));
        verify(orderRepository).findByStatus(status, pageable);
    }
}
