package org.acs.stuco.backend.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acs.stuco.backend.auth.JwtService;
import org.acs.stuco.backend.order.archive.ArchivedOrder;
import org.acs.stuco.backend.order.archive.ArchivedOrderRepository;
import org.acs.stuco.backend.product.Product;
import org.acs.stuco.backend.product.ProductRepository;
import org.acs.stuco.backend.user.Role;
import org.acs.stuco.backend.user.User;
import org.acs.stuco.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class OrderControllerIntegrationTest
{

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArchivedOrderRepository archivedOrderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User regularUser;
    private User repUser;
    private User stucoUser;
    private User adminUser;
    private Product testProduct;
    private Product limitedProduct;
    private Product unlimitedProduct;
    private Order testOrder;
    private ArchivedOrder archivedOrder;
    private String regularUserToken;
    private String repUserToken;
    private String stucoUserToken;
    private String adminUserToken;

    @BeforeEach
    void setUp()
    {
        // Clear any existing data
        orderRepository.deleteAll();
        archivedOrderRepository.deleteAll();
        productRepository.deleteAll();

        // Create test users with different roles
        regularUser = createUser("regular@acsbg.org", "Regular User", Role.USER);
        repUser = createUser("rep@acsbg.org", "Rep User", Role.CLASS_REP);
        stucoUser = createUser("stuco@acsbg.org", "Stuco User", Role.STUCO);
        adminUser = createUser("admin@acsbg.org", "Admin User", Role.ADMIN);

        // Generate tokens for later use
        regularUserToken = "Bearer " + jwtService.generateToken(regularUser);
        repUserToken = "Bearer " + jwtService.generateToken(repUser);
        stucoUserToken = "Bearer " + jwtService.generateToken(stucoUser);
        adminUserToken = "Bearer " + jwtService.generateToken(adminUser);

        // Create test products
        testProduct = createProduct("Regular Product", "This is a regular product", new BigDecimal("25.99"), 50);
        limitedProduct = createProduct("Limited Product", "This product has limited stock", new BigDecimal("39.99"), 3);
        unlimitedProduct = createProduct("Unlimited Product", "This product has unlimited stock", new BigDecimal("15.99"), -1);

        // Create a test order
        testOrder = createOrder(testProduct, regularUser, 2, OrderStatus.PENDING, PaymentType.CASH);

        // Create an archived order for testing
        archivedOrder = createArchivedOrder();
    }

    private User createUser(String email, String name, Role role)
    {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setName(name);
        user.setRole(role);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private Product createProduct(String name, String description, BigDecimal price, int available)
    {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setAvailable(available);
        product.setImageUrl("http://example.com/" + name.toLowerCase().replace(" ", "-") + ".jpg");
        product.setCreatedBy(stucoUser.getId());
        return productRepository.save(product);
    }

    private Order createOrder(Product product, User buyer, int quantity, OrderStatus status, PaymentType paymentType)
    {
        Order order = new Order();
        order.setProduct(product);
        order.setBuyer(buyer);
        order.setQuantity(quantity);
        order.setStatus(status);
        order.setPaymentType(paymentType);
        order.setCreatedAt(LocalDateTime.now());
        order.setProductName(product.getName());
        order.setProductPrice(product.getPrice());
        order.setInstructions("Please deliver to room 101");
        return orderRepository.save(order);
    }

    private ArchivedOrder createArchivedOrder()
    {
        ArchivedOrder order = new ArchivedOrder();
        order.setId(999L);
        order.setProduct(testProduct);
        order.setBuyer(regularUser);
        order.setQuantity(1);
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentType(PaymentType.PREPAID);
        order.setCreatedAt(LocalDateTime.now().minusDays(45));
        order.setPaidAt(LocalDateTime.now().minusDays(44));
        order.setAssignedRep(repUser);
        order.setProductName(testProduct.getName());
        order.setProductPrice(testProduct.getPrice());
        return archivedOrderRepository.save(order);
    }


    @Test
    @DisplayName("POST /api/orders should create order with unlimited stock product")
    void createOrderShouldCreateWithUnlimitedStockProduct() throws Exception
    {
        // Create request object
        Order newOrder = new Order();
        newOrder.setProduct(unlimitedProduct);
        newOrder.setQuantity(100); // Large quantity for unlimited stock
        newOrder.setPaymentType(PaymentType.PREPAID);

        // Perform request with regular user
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newOrder))
                        .header("Authorization", regularUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.paymentType").value("PREPAID"));

        // Verify product stock remains unlimited (-1)
        Product unchangedProduct = productRepository.findById(unlimitedProduct.getId()).orElseThrow();
        assertThat(unchangedProduct.getAvailable()).isEqualTo(-1);
    }

    @Test
    @DisplayName("POST /api/orders should create order with custom product details")
    void createOrderShouldCreateWithCustomProductDetails() throws Exception
    {
        // Create request object with custom product details
        Order newOrder = new Order();
        newOrder.setProductName("Custom T-Shirt");
        newOrder.setProductPrice(new BigDecimal("20.00"));
        newOrder.setQuantity(1);
        newOrder.setPaymentType(PaymentType.CASH);

        // Perform request with regular user
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newOrder))
                        .header("Authorization", regularUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Custom T-Shirt"))
                .andExpect(jsonPath("$.productPrice").value(20.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }


    @Test
    @DisplayName("GET /api/orders should return user's orders")
    void getUserOrdersShouldReturnUserOrders() throws Exception
    {
        // Perform request with regular user
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", regularUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(testOrder.getId()))
                .andExpect(jsonPath("$.content[0].buyer.id").value(regularUser.getId()));
    }

    @Test
    @DisplayName("GET /api/orders/admin should return all orders when authorized")
    void getAllOrdersShouldReturnAllOrdersWhenAuthorized() throws Exception
    {
        // Create a second order for a different user
        createOrder(unlimitedProduct, repUser, 3, OrderStatus.PAID, PaymentType.PREPAID);

        // Perform request with STUCO user (authorized)
        mockMvc.perform(get("/api/orders/admin")
                        .header("Authorization", stucoUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].buyer.id", containsInAnyOrder(
                        regularUser.getId().intValue(),
                        repUser.getId().intValue())));

        // Perform request with ADMIN user (also authorized)
        mockMvc.perform(get("/api/orders/admin")
                        .header("Authorization", adminUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/orders/admin should return 403 when unauthorized")
    void getAllOrdersShouldReturn403WhenUnauthorized() throws Exception
    {
        // Perform request with regular user (unauthorized)
        mockMvc.perform(get("/api/orders/admin")
                        .header("Authorization", regularUserToken))
                .andExpect(status().isForbidden()); // 403 Forbidden

        // Perform request with REP user (also unauthorized)
        mockMvc.perform(get("/api/orders/admin")
                        .header("Authorization", repUserToken))
                .andExpect(status().isForbidden()); // 403 Forbidden
    }


    @Test
    @DisplayName("GET /api/orders/assigned should return orders assigned to rep")
    void getAssignedOrdersShouldReturnOrdersAssignedToRep() throws Exception
    {
        // Assign the test order to the REP user
        testOrder.setAssignedRep(repUser);
        orderRepository.save(testOrder);

        // Perform request with REP user (authorized)
        mockMvc.perform(get("/api/orders/assigned")
                        .header("Authorization", repUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(testOrder.getId()))
                .andExpect(jsonPath("$.content[0].assignedRep.id").value(repUser.getId()));
    }

    @Test
    @DisplayName("GET /api/orders/assigned should return 403 when not a rep")
    void getAssignedOrdersShouldReturn403WhenNotRep() throws Exception
    {
        // Perform request with regular user (unauthorized)
        mockMvc.perform(get("/api/orders/assigned")
                        .header("Authorization", regularUserToken))
                .andExpect(status().isForbidden()); // 403 Forbidden
    }

    @Test
    @DisplayName("GET /api/orders/archived should return archived orders when authorized")
    void getArchivedOrdersShouldReturnArchivedOrdersWhenAuthorized() throws Exception
    {
        // Perform request with STUCO user (authorized)
        mockMvc.perform(get("/api/orders/archived")
                        .header("Authorization", stucoUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(archivedOrder.getId()))
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.content[0].buyer.id").value(regularUser.getId()));

        // Perform request with ADMIN user (also authorized)
        mockMvc.perform(get("/api/orders/archived")
                        .header("Authorization", adminUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

}
