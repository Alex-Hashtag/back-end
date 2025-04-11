package org.acs.stuco.backend.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.acs.stuco.backend.auth.JwtService;
import org.acs.stuco.backend.upload.UploadService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerIntegrationTest
{

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UploadService uploadService;

    @Autowired
    private EntityManager entityManager;

    private User regularUser;
    private User stucoUser;
    private Product testProduct;
    private Product outOfStockProduct;
    private Product unlimitedProduct;

    @BeforeEach
    void setUp()
    {

        productRepository.deleteAll();

        regularUser = new User();
        regularUser.setEmail("regular@acsbg.org");
        regularUser.setPasswordHash(passwordEncoder.encode("password"));
        regularUser.setName("User");
        regularUser.setRole(Role.USER);
        regularUser.setEmailVerified(true);
        userRepository.save(regularUser);

        stucoUser = new User();
        stucoUser.setEmail("stuco@acsbg.org");
        stucoUser.setPasswordHash(passwordEncoder.encode("password"));
        stucoUser.setName("Stuco");
        stucoUser.setRole(Role.STUCO);
        stucoUser.setEmailVerified(true);
        userRepository.save(stucoUser);

        testProduct = new Product();
        testProduct.setName("Test Product");
        testProduct.setDescription("This is a test product");
        testProduct.setPrice(new BigDecimal("25.99"));
        testProduct.setAvailable(10);
        testProduct.setImageUrl("http://example.com/test-image.jpg");
        testProduct.setCreatedBy(stucoUser.getId());
        productRepository.save(testProduct);

        outOfStockProduct = new Product();
        outOfStockProduct.setName("Out of Stock Product");
        outOfStockProduct.setDescription("This product is out of stock");
        outOfStockProduct.setPrice(new BigDecimal("15.99"));
        outOfStockProduct.setAvailable(0);
        outOfStockProduct.setCreatedBy(stucoUser.getId());
        productRepository.save(outOfStockProduct);

        unlimitedProduct = new Product();
        unlimitedProduct.setName("Unlimited Product");
        unlimitedProduct.setDescription("This product has unlimited stock");
        unlimitedProduct.setPrice(new BigDecimal("5.99"));
        unlimitedProduct.setAvailable(-1); // -1 means unlimited
        unlimitedProduct.setCreatedBy(stucoUser.getId());
        productRepository.save(unlimitedProduct);
    }

    @Test
    @DisplayName("GET /api/products should return available products")
    void listAvailableProductsShouldReturnAvailableProducts() throws Exception
    {
        mockMvc.perform(get("/api/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2))) // should return testProduct and unlimitedProduct, but not outOfStockProduct
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Test Product", "Unlimited Product")))
                .andExpect(jsonPath("$.content[*].available", containsInAnyOrder(10, -1)));
    }

    @Test
    @DisplayName("GET /api/products/all should return all products when authorized")
    void listAllProductsShouldReturnAllProductsWhenAuthorized() throws Exception
    {
        mockMvc.perform(get("/api/products/all")
                        .header("Authorization", getAuthHeaderForUser(stucoUser))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3))) // should return all 3 products
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Test Product", "Out of Stock Product", "Unlimited Product")));
    }

    @Test
    @DisplayName("GET /api/products/all should return 200 when unauthorized")
    void listAllProductsShouldReturn200WhenUnauthorized() throws Exception
    {
        mockMvc.perform(get("/api/products/all")
                        .header("Authorization", getAuthHeaderForUser(regularUser))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/products/{id} should return product when it exists and is available")
    void getProductShouldReturnProductWhenExistsAndAvailable() throws Exception
    {
        mockMvc.perform(get("/api/products/{id}", testProduct.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(testProduct.getName()))
                .andExpect(jsonPath("$.description").value(testProduct.getDescription()))
                .andExpect(jsonPath("$.price").value(testProduct.getPrice().doubleValue()))
                .andExpect(jsonPath("$.available").value(testProduct.getAvailable()));
    }

    @Test
    @DisplayName("GET /api/products/{id} should return 404 when product not found or not available")
    void getProductShouldReturn404WhenNotFoundOrNotAvailable() throws Exception
    {

        mockMvc.perform(get("/api/products/{id}", outOfStockProduct.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/products/{id}", 999L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/products should create product when authorized")
    void createProductShouldCreateWhenAuthorized() throws Exception
    {

        Product newProduct = new Product();
        newProduct.setName("New Test Product");
        newProduct.setDescription("This is a new test product");
        newProduct.setPrice(new BigDecimal("30.99"));
        newProduct.setAvailable(5);

        int initialCount = productRepository.findAll().size();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProduct))
                        .header("Authorization", getAuthHeaderForUser(stucoUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Test Product"))
                .andExpect(jsonPath("$.description").value("This is a new test product"))
                .andExpect(jsonPath("$.price").value(30.99))
                .andExpect(jsonPath("$.available").value(5));

        List<Product> allProducts = productRepository.findAll();
        assertThat(allProducts).hasSize(initialCount + 1);

        Product createdProduct = allProducts.stream()
                .filter(p -> p.getName().equals("New Test Product"))
                .findFirst()
                .orElse(null);

        assertThat(createdProduct).isNotNull();
        assertThat(createdProduct.getCreatedBy()).isEqualTo(stucoUser.getId());
    }

    @Test
    @DisplayName("POST /api/products should return 403 when unauthorized")
    void createProductShouldReturn403WhenUnauthorized() throws Exception
    {

        Product newProduct = new Product();
        newProduct.setName("Unauthorized Product");
        newProduct.setDescription("This should not be created");
        newProduct.setPrice(new BigDecimal("10.99"));
        newProduct.setAvailable(3);

        int initialCount = productRepository.findAll().size();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProduct))
                        .header("Authorization", getAuthHeaderForUser(regularUser)))
                .andExpect(status().isForbidden());

        assertThat(productRepository.findAll()).hasSize(initialCount);
    }

    @Test
    @DisplayName("PUT /api/products/{id} should update product when authorized")
    void updateProductShouldUpdateWhenAuthorized() throws Exception
    {

        Product updatedProduct = new Product();
        updatedProduct.setName("Updated Product");
        updatedProduct.setDescription("This product has been updated");
        updatedProduct.setPrice(new BigDecimal("50.00"));
        updatedProduct.setAvailable(20);

        mockMvc.perform(put("/api/products/{id}", testProduct.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedProduct))
                        .header("Authorization", getAuthHeaderForUser(stucoUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Product"))
                .andExpect(jsonPath("$.description").value("This product has been updated"))
                .andExpect(jsonPath("$.price").value(50.00))
                .andExpect(jsonPath("$.available").value(20));

        Product updated = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Product");
        assertThat(updated.getDescription()).isEqualTo("This product has been updated");
        assertThat(updated.getPrice()).isEqualTo(new BigDecimal("50.00"));
        assertThat(updated.getAvailable()).isEqualTo(20);
    }

    @Test
    @DisplayName("PUT /api/products/{id} with image should update product and image when authorized")
    void updateProductWithImageShouldUpdateWhenAuthorized() throws Exception
    {

        Product updatedProduct = new Product();
        updatedProduct.setName("Updated Product With Image");
        updatedProduct.setDescription("This product has been updated with image");
        updatedProduct.setPrice(new BigDecimal("45.00"));
        updatedProduct.setAvailable(15);

        String productJson = objectMapper.writeValueAsString(updatedProduct);

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                productJson.getBytes()
        );

        MockMultipartFile filePart = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );


        mockMvc.perform(put("/api/products/{id}", testProduct.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedProduct))
                        .header("Authorization", getAuthHeaderForUser(stucoUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Product With Image"));
    }

    @Test
    @DisplayName("PATCH /api/products/{id} should partially update product when authorized")
    void patchProductShouldPartiallyUpdateWhenAuthorized() throws Exception
    {

        Product partialUpdate = new Product();
        partialUpdate.setName("Patched Product Name");

        mockMvc.perform(patch("/api/products/{id}", testProduct.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(partialUpdate))
                        .header("Authorization", getAuthHeaderForUser(stucoUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Patched Product Name"))

                .andExpect(jsonPath("$.description").value(testProduct.getDescription()))
                .andExpect(jsonPath("$.price").value(testProduct.getPrice().doubleValue()))
                .andExpect(jsonPath("$.available").value(testProduct.getAvailable()));

        Product updated = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Patched Product Name");
        assertThat(updated.getDescription()).isEqualTo(testProduct.getDescription());
        assertThat(updated.getPrice()).isEqualTo(testProduct.getPrice());
        assertThat(updated.getAvailable()).isEqualTo(testProduct.getAvailable());
    }


    @Test
    @DisplayName("DELETE /api/products/{id} should return 403 when unauthorized")
    void deleteProductShouldReturn403WhenUnauthorized() throws Exception
    {
        mockMvc.perform(delete("/api/products/{id}", testProduct.getId())
                        .header("Authorization", getAuthHeaderForUser(regularUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/products/cleanup should delete out of stock products when authorized")
    void cleanupShouldDeleteOutOfStockProductsWhenAuthorized() throws Exception
    {
        mockMvc.perform(post("/api/products/cleanup")
                        .header("Authorization", getAuthHeaderForUser(stucoUser)))
                .andExpect(status().isNoContent());
    }


    @Test
    @DisplayName("POST /api/products/{id}/upload-image should return 200 when authorized")
    void uploadProductImageShouldWork() throws Exception
    {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/products/{id}/upload-image", testProduct.getId())
                        .file(file)
                        .header("Authorization", getAuthHeaderForUser(stucoUser)))
                .andExpect(status().isOk());
    }

    private String getAuthHeaderForUser(User user)
    {
        String token = jwtService.generateToken(user);
        return "Bearer " + token;
    }
}


