package org.acs.stuco.backend.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(locations = "classpath:application-test.properties")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private User testUser;
    private Page<User> userPage;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@acsbg.org");
        testUser.setName("Test User");
        testUser.setRole(Role.USER);
        testUser.setAvatarUrl("http://example.com/avatar.jpg");
        testUser.setCollectedBalance(new BigDecimal("0.00"));

        userPage = new PageImpl<>(List.of(testUser));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsers_WhenAdmin_ShouldReturnUsers() throws Exception {
        // Arrange
        when(userService.getAllUsers(any(Pageable.class))).thenReturn(userPage);

        // Act & Assert
        mockMvc.perform(get("/api/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(testUser.getId()))
                .andExpect(jsonPath("$.content[0].email").value(testUser.getEmail()));

        verify(userService).getAllUsers(any(Pageable.class));
    }


    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserById_WhenAdmin_ShouldReturnUser() throws Exception {
        // Arrange
        when(userService.getUserById(1L)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(get("/api/users/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId()))
                .andExpect(jsonPath("$.email").value(testUser.getEmail()));

        verify(userService).getUserById(1L);
    }

    @Test
    @WithMockUser
    void getCurrentUser_ShouldReturnCurrentUser() throws Exception {
        // Arrange
        when(userService.getCurrentUser()).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(get("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId()))
                .andExpect(jsonPath("$.email").value(testUser.getEmail()));

        verify(userService).getCurrentUser();
    }

    @Test
    @WithMockUser
    void updateCurrentUser_ShouldUpdateAndReturnUser() throws Exception {
        // Arrange
        User updatedUser = new User();
        updatedUser.setName("Updated Name");

        when(userService.updateCurrentUser(any(User.class))).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(put("/api/users/me")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId()))
                .andExpect(jsonPath("$.email").value(testUser.getEmail()));

        verify(userService).updateCurrentUser(any(User.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUserRole_WhenAdmin_ShouldUpdateAndReturnUser() throws Exception {
        // Arrange
        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setRole(Role.CLASS_REP);

        when(userService.updateUserRole(1L, Role.CLASS_REP)).thenReturn(updatedUser);

        // Act & Assert
        mockMvc.perform(patch("/api/users/1/role")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .param("newRole", "CLASS_REP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(updatedUser.getId()))
                .andExpect(jsonPath("$.role").value(updatedUser.getRole().toString()));

        verify(userService).updateUserRole(1L, Role.CLASS_REP);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllRoles_ShouldReturnAllRoles() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/users/roles")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4))); // USER, CLASS_REP, STUCO, ADMIN
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getClassRepsWithBalances_WhenAdmin_ShouldReturnClassReps() throws Exception {
        // Arrange
        User classRep = new User();
        classRep.setId(2L);
        classRep.setEmail("rep@acsbg.org");
        classRep.setRole(Role.CLASS_REP);
        classRep.setCollectedBalance(new BigDecimal("100.00"));

        Page<User> classRepsPage = new PageImpl<>(List.of(classRep));
        when(userService.getClassRepsWithBalances(any(Pageable.class))).thenReturn(classRepsPage);

        // Act & Assert
        mockMvc.perform(get("/api/users/class-reps")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(classRep.getId()))
                .andExpect(jsonPath("$.content[0].role").value(classRep.getRole().toString()))
                .andExpect(jsonPath("$.content[0].collectedBalance").value("100.0"));

        verify(userService).getClassRepsWithBalances(any(Pageable.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void clearRepBalance_WhenAdmin_ShouldClearBalance() throws Exception {
        // Arrange
        doNothing().when(userService).clearUserBalance(1L);

        // Act & Assert
        mockMvc.perform(post("/api/users/1/balance/clear")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(userService).clearUserBalance(1L);
    }

    @Test
    @WithMockUser
    void uploadProfilePicture_ShouldUploadAndReturnUser() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        when(userService.uploadProfilePicture(any())).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/users/upload-pfp")
                .file(file)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId()))
                .andExpect(jsonPath("$.email").value(testUser.getEmail()));

        verify(userService).uploadProfilePicture(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUser_WhenAdmin_ShouldDeleteUser() throws Exception {
        // Arrange
        doNothing().when(userService).deleteUser(1L);

        // Act & Assert
        mockMvc.perform(delete("/api/users/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void filterUsers_WhenAdmin_ShouldReturnFilteredUsers() throws Exception {
        // Arrange
        when(userService.filterUsers(
                anyList(),
                anyString(),
                anyInt(),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                anyBoolean(),
                any(Pageable.class)
        )).thenReturn(userPage);

        // Act & Assert
        mockMvc.perform(get("/api/users/search")
                .param("roles", "USER", "CLASS_REP")
                .param("searchTerm", "test")
                .param("graduationYear", "2025")
                .param("balanceEq", "100.00")
                .param("balanceGt", "50.00")
                .param("balanceLt", "200.00")
                .param("activeOrders", "true")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(testUser.getId()));

        verify(userService).filterUsers(
                anyList(),
                anyString(),
                anyInt(),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                anyBoolean(),
                any(Pageable.class)
        );
    }
}
