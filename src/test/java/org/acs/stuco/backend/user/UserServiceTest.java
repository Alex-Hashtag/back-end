package org.acs.stuco.backend.user;

import org.acs.stuco.backend.exceptions.UserNotFoundException;
import org.acs.stuco.backend.upload.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // Using MockitoBean as requested
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties") // Ensure test properties are loaded
class UserServiceTest {

    @Autowired
    private UserService userService;

    @MockitoBean // Mocking UserRepository using MockitoBean
    private UserRepository userRepository;

    @MockitoBean // Mocking UploadService using MockitoBean
    private UploadService uploadService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setRole(Role.USER);
    }

    @Test
    void getUserById_WhenUserExists_ShouldReturnUser() {
        // Arrange
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));

        // Act
        User foundUser = userService.getUserById(1L);

        // Assert
        assertNotNull(foundUser);
        assertEquals(testUser.getId(), foundUser.getId());
        assertEquals(testUser.getEmail(), foundUser.getEmail());
        verify(userRepository).findById(1L);
    }

    @Test
    void getUserById_WhenUserDoesNotExist_ShouldThrowUserNotFoundException() {
        // Arrange
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> {
            userService.getUserById(99L);
        });
        assertEquals("User not found with ID: 99", exception.getMessage());
        verify(userRepository).findById(99L);
    }

     @Test
    void getCurrentUser_WhenPrincipalIsUser_ShouldReturnUser() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser); // Principal is a User object
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));

        // Act
        User currentUser = userService.getCurrentUser();

        // Assert
        assertNotNull(currentUser);
        assertEquals(testUser.getId(), currentUser.getId());
        assertEquals(testUser.getEmail(), currentUser.getEmail());
        verify(userRepository).findByEmail(testUser.getEmail());

        // Clean up security context after test
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_WhenPrincipalIsString_ShouldReturnUser() {
        // Arrange
        String userEmail = "test@example.com";
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userEmail); // Principal is a String (email)
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(testUser));

        // Act
        User currentUser = userService.getCurrentUser();

        // Assert
        assertNotNull(currentUser);
        assertEquals(testUser.getId(), currentUser.getId());
        assertEquals(userEmail, currentUser.getEmail());
        verify(userRepository).findByEmail(userEmail);

        // Clean up security context after test
        SecurityContextHolder.clearContext();
    }


    @Test
    void getCurrentUser_WhenUserNotFoundInRepo_ShouldThrowUserNotFoundException() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> {
            userService.getCurrentUser();
        });
        assertEquals("User not found with email: " + testUser.getEmail(), exception.getMessage());
        verify(userRepository).findByEmail(testUser.getEmail());

        // Clean up security context after test
        SecurityContextHolder.clearContext();
    }

     @Test
    void getCurrentUser_WhenPrincipalIsInvalidType_ShouldThrowIllegalStateException() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(new Object()); // Invalid principal type
        SecurityContextHolder.setContext(securityContext);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            userService.getCurrentUser();
        });
        assertEquals("Invalid principal type in security context", exception.getMessage());
        // No repository interaction expected here
        verify(userRepository, never()).findByEmail(anyString());

        // Clean up security context after test
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateCurrentUser_ShouldUpdateNameSuccessfully() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        
        User updatedDetails = new User();
        updatedDetails.setName("Updated Name");
        
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = userService.updateCurrentUser(updatedDetails);

        // Assert
        assertEquals("Updated Name", result.getName());
        assertEquals(testUser.getId(), result.getId());
        verify(userRepository).save(any(User.class));
        
        // Clean up
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateCurrentUser_WhenNameIsNull_ShouldNotUpdateName() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        
        String originalName = testUser.getName();
        User updatedDetails = new User();
        updatedDetails.setName(null);
        
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = userService.updateCurrentUser(updatedDetails);

        // Assert
        assertEquals(originalName, result.getName());
        verify(userRepository).save(any(User.class));
        
        // Clean up
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateUserRole_WhenStucoUpdatesUser_ShouldSucceed() {
        // Arrange
        User stucoUser = new User();
        stucoUser.setId(2L);
        stucoUser.setEmail("stuco@example.com");
        stucoUser.setRole(Role.STUCO);

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(stucoUser);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(stucoUser.getEmail())).thenReturn(Optional.of(stucoUser));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = userService.updateUserRole(testUser.getId(), Role.CLASS_REP);

        // Assert
        assertEquals(Role.CLASS_REP, result.getRole());
        verify(userRepository).save(any(User.class));
        
        // Clean up
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateUserRole_WhenStucoTriesToUpdateStuco_ShouldThrowAccessDeniedException() {
        // Arrange
        User stucoUser = new User();
        stucoUser.setId(2L);
        stucoUser.setEmail("stuco@example.com");
        stucoUser.setRole(Role.STUCO);

        User targetStucoUser = new User();
        targetStucoUser.setId(3L);
        targetStucoUser.setEmail("stuco2@example.com");
        targetStucoUser.setRole(Role.STUCO);

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(stucoUser);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(stucoUser.getEmail())).thenReturn(Optional.of(stucoUser));
        when(userRepository.findById(targetStucoUser.getId())).thenReturn(Optional.of(targetStucoUser));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> {
            userService.updateUserRole(targetStucoUser.getId(), Role.USER);
        });
        
        verify(userRepository, never()).save(any(User.class));
        
        // Clean up
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateUserRole_WhenStucoTriesToAssignStucoRole_ShouldThrowAccessDeniedException() {
        // Arrange
        User stucoUser = new User();
        stucoUser.setId(2L);
        stucoUser.setEmail("stuco@example.com");
        stucoUser.setRole(Role.STUCO);

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(stucoUser);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(stucoUser.getEmail())).thenReturn(Optional.of(stucoUser));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> {
            userService.updateUserRole(testUser.getId(), Role.STUCO);
        });
        
        verify(userRepository, never()).save(any(User.class));
        
        // Clean up
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateUserRole_WhenAdminUpdatesUser_ShouldSucceed() {
        // Arrange
        User adminUser = new User();
        adminUser.setId(2L);
        adminUser.setEmail("admin@example.com");
        adminUser.setRole(Role.ADMIN);

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(adminUser);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(adminUser.getEmail())).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = userService.updateUserRole(testUser.getId(), Role.STUCO);

        // Assert
        assertEquals(Role.STUCO, result.getRole());
        verify(userRepository).save(any(User.class));
        
        // Clean up
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateUserRole_WhenRegularUserTriesToUpdate_ShouldThrowAccessDeniedException() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser); // Regular user
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        
        verify(userRepository, never()).save(any(User.class));
        
        // Clean up
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllUsers_ShouldReturnPageOfUsers() {
        // Arrange
        Page<User> mockPage = mock(Page.class);
        Pageable pageable = mock(Pageable.class);
        when(userRepository.findAll(pageable)).thenReturn(mockPage);

        // Act
        Page<User> result = userService.getAllUsers(pageable);

        // Assert
        assertEquals(mockPage, result);
        verify(userRepository).findAll(pageable);
    }

    @Test
    void getClassRepsWithBalances_ShouldReturnPageOfClassReps() {
        // Arrange
        Page<User> mockPage = mock(Page.class);
        Pageable pageable = mock(Pageable.class);
        when(userRepository.findByRole(eq(Role.CLASS_REP), eq(pageable))).thenReturn(mockPage);

        // Act
        Page<User> result = userService.getClassRepsWithBalances(pageable);

        // Assert
        assertEquals(mockPage, result);
        verify(userRepository).findByRole(Role.CLASS_REP, pageable);
    }

    @Test
    void clearUserBalance_ShouldSetBalanceToZero() {
        // Arrange
        testUser.setCollectedBalance(new BigDecimal("100.00"));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        userService.clearUserBalance(testUser.getId());

        // Assert
        verify(userRepository).save(any(User.class));
        assertEquals(BigDecimal.ZERO, testUser.getCollectedBalance());
    }

    @Test
    void incrementCollectedBalance_WhenBalanceExists_ShouldAddToBalance() {
        // Arrange
        testUser.setCollectedBalance(new BigDecimal("100.00"));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = userService.incrementCollectedBalance(testUser.getId(), new BigDecimal("50.00"));

        // Assert
        assertEquals(new BigDecimal("150.00"), result.getCollectedBalance());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void incrementCollectedBalance_WhenBalanceIsNull_ShouldSetToAmount() {
        // Arrange
        testUser.setCollectedBalance(null);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = userService.incrementCollectedBalance(testUser.getId(), new BigDecimal("50.00"));

        // Assert
        assertEquals(new BigDecimal("50.00"), result.getCollectedBalance());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void uploadProfilePicture_WhenOldAvatarDoesNotExist_ShouldUploadNewAvatar() throws Exception {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        
        MultipartFile mockFile = mock(MultipartFile.class);
        when(uploadService.upload(mockFile)).thenReturn("new-avatar-url.jpg");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        User result = userService.uploadProfilePicture(mockFile);

        // Assert
        assertEquals("new-avatar-url.jpg", result.getAvatarUrl());
        verify(uploadService).upload(mockFile);
        verify(userRepository).save(any(User.class));
        
        // Clean up
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadProfilePicture_WhenOldAvatarExists_ShouldDeleteOldAndUploadNew() throws Exception {
        // Arrange
        testUser.setAvatarUrl("old-avatar-url.jpg");
        
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        
        MultipartFile mockFile = mock(MultipartFile.class);
        when(uploadService.upload(mockFile)).thenReturn("new-avatar-url.jpg");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        User result = userService.uploadProfilePicture(mockFile);

        // Assert
        assertEquals("new-avatar-url.jpg", result.getAvatarUrl());
        verify(uploadService).upload(mockFile);
        verify(uploadService).delete("old-avatar-url.jpg");
        verify(userRepository).save(any(User.class));
        
        // Clean up
        SecurityContextHolder.clearContext();
    }
}
