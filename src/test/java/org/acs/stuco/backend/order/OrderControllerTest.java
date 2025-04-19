package org.acs.stuco.backend.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acs.stuco.backend.order.archive.ArchivedOrder;
import org.acs.stuco.backend.product.Product;
import org.acs.stuco.backend.user.Role;
import org.acs.stuco.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(locations = "classpath:application-test.properties")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private Order testOrder;
    private Product testProduct;
    private User testBuyer;
    private UserDetails regularUserDetails;
    private UserDetails repUserDetails;
    private UserDetails stucoUserDetails;
    private final Long testOrderId = 1L;

    @BeforeEach
    void setUp() {
        // Initialize test product
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Test Product");
        testProduct.setPrice(new BigDecimal("10.00"));
        
        // Initialize test buyer
        testBuyer = new User();
        testBuyer.setId(1L);
        testBuyer.setEmail("user@acsbg.org");
        testBuyer.setName("Regular User");

        // Initialize test order
        testOrder = Order.builder()
            .id(testOrderId)
            .buyer(testBuyer)
            .product(testProduct)
            .productName("Test Product")
            .productPrice(new BigDecimal("10.00"))
            .quantity(2)
            .status(OrderStatus.PENDING)
            .paymentType(PaymentType.CASH)
            .createdAt(LocalDateTime.now().minusDays(1))
            .instructions("Test instructions")
            .build();

        // Create UserDetails for testing
        regularUserDetails = org.springframework.security.core.userdetails.User.builder()
            .username("user@acsbg.org")
            .password("password")
            .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
            .build();

        repUserDetails = org.springframework.security.core.userdetails.User.builder()
            .username("rep@acsbg.org")
            .password("password")
            .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_REP")))
            .build();

        stucoUserDetails = org.springframework.security.core.userdetails.User.builder()
            .username("stuco@acsbg.org")
            .password("password")
            .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUCO")))
            .build();
    }

    @Test
    void createOrder_WithAuthenticatedUser_ShouldCreateOrder() throws Exception {
        // Arrange
        when(orderService.createOrder(any(Order.class))).thenReturn(testOrder);

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                .with(user(regularUserDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testOrder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testOrder.getId()))
                .andExpect(jsonPath("$.productName").value(testOrder.getProductName()))
                .andExpect(jsonPath("$.quantity").value(testOrder.getQuantity()));

        verify(orderService).createOrder(any(Order.class));
    }
}
