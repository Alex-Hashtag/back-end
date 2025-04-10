package org.acs.stuco.backend.user;

import org.acs.stuco.backend.exceptions.UserNotFoundException;
import org.acs.stuco.backend.upload.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class UserServiceTest
{

    @Mock
    private UserRepository userRepository;

    @Mock
    private UploadService uploadService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private User adminUser;
    private User stucoUser;
    private User classRepUser;

    @BeforeEach
    void setUp()
    {

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@acsbg.org");
        testUser.setName("Test User");
        testUser.setPasswordHash("hashed_password");
        testUser.setRole(Role.USER);
        testUser.setEmailVerified(true);

        classRepUser = new User();
        classRepUser.setId(2L);
        classRepUser.setEmail("rep@acsbg.org");
        classRepUser.setName("Class Rep");
        classRepUser.setPasswordHash("hashed_password");
        classRepUser.setRole(Role.CLASS_REP);
        classRepUser.setCollectedBalance(BigDecimal.valueOf(100.00));

        stucoUser = new User();
        stucoUser.setId(3L);
        stucoUser.setEmail("stuco@acsbg.org");
        stucoUser.setName("Stuco Member");
        stucoUser.setPasswordHash("hashed_password");
        stucoUser.setRole(Role.STUCO);

        adminUser = new User();
        adminUser.setId(4L);
        adminUser.setEmail("admin@acsbg.org");
        adminUser.setName("Admin");
        adminUser.setPasswordHash("hashed_password");
        adminUser.setRole(Role.ADMIN);

        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("getAllUsers: Should return paginated list of users")
    void getAllUsersShouldReturnPaginatedList()
    {

        List<User> userList = Arrays.asList(testUser, classRepUser);
        Page<User> userPage = new PageImpl<>(userList);
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findAll(pageable)).thenReturn(userPage);

        Page<User> result = userService.getAllUsers(pageable);

        assertThat(result).isEqualTo(userPage);
        assertThat(result.getContent()).hasSize(2);
        verify(userRepository).findAll(pageable);
    }

    @Test
    @DisplayName("getUserById: Should return user when exists")
    void getUserByIdShouldReturnUserWhenExists()
    {

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        User result = userService.getUserById(1L);

        assertThat(result).isEqualTo(testUser);
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("getUserById: Should throw exception when user not found")
    void getUserByIdShouldThrowExceptionWhenUserNotFound()
    {

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found with ID: 99");
    }

    @Test
    @DisplayName("getCurrentUser: Should return authenticated user when exists")
    void getCurrentUserShouldReturnAuthenticatedUserWhenExists()
    {

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));

        User result = userService.getCurrentUser();

        assertThat(result).isEqualTo(testUser);
        verify(userRepository).findByEmail(testUser.getEmail());
    }

    @Test
    @DisplayName("getCurrentUser: Should handle string principal correctly")
    void getCurrentUserShouldHandleStringPrincipal()
    {

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("user@acsbg.org");
        when(userRepository.findByEmail("user@acsbg.org")).thenReturn(Optional.of(testUser));

        User result = userService.getCurrentUser();

        assertThat(result).isEqualTo(testUser);
        verify(userRepository).findByEmail("user@acsbg.org");
    }

    @Test
    @DisplayName("getCurrentUser: Should throw exception when user not found")
    void getCurrentUserShouldThrowExceptionWhenUserNotFound()
    {

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser())
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found with email");
    }

    @Test
    @DisplayName("updateCurrentUser: Should update user name")
    void updateCurrentUserShouldUpdateUserName()
    {

        User updatedUser = new User();
        updatedUser.setName("Updated Name");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateCurrentUser(updatedUser);

        assertThat(result.getName()).isEqualTo("Updated Name");
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("updateUserRole: Admin should update any user role below admin")
    void updateUserRoleAdminShouldUpdateAnyUserRoleBelowAdmin()
    {

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(adminUser);
        when(userRepository.findByEmail(adminUser.getEmail())).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUserRole(testUser.getId(), Role.STUCO);

        assertThat(result.getRole()).isEqualTo(Role.STUCO);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("updateUserRole: STUCO should only update to USER or CLASS_REP")
    void updateUserRoleStucoShouldOnlyUpdateToUserOrClassRep()
    {

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(stucoUser);
        when(userRepository.findByEmail(stucoUser.getEmail())).thenReturn(Optional.of(stucoUser));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUserRole(testUser.getId(), Role.CLASS_REP);

        assertThat(result.getRole()).isEqualTo(Role.CLASS_REP);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("updateUserRole: STUCO cannot update to STUCO or ADMIN")
    void updateUserRoleStucoCannotUpdateToStucoOrAdmin()
    {

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(stucoUser);
        when(userRepository.findByEmail(stucoUser.getEmail())).thenReturn(Optional.of(stucoUser));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.updateUserRole(testUser.getId(), Role.ADMIN))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("STUCO can only assign USER or CLASS_REP");
    }

    @Test
    @DisplayName("updateUserRole: STUCO cannot update users with STUCO or ADMIN role")
    void updateUserRoleStucoCannotUpdateHigherRoles()
    {

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(stucoUser);
        when(userRepository.findByEmail(stucoUser.getEmail())).thenReturn(Optional.of(stucoUser));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> userService.updateUserRole(adminUser.getId(), Role.USER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("STUCO cannot update users with STUCO or ADMIN role");
    }

    @Test
    @DisplayName("getClassRepsWithBalances: Should return paginated list of class reps")
    void getClassRepsWithBalancesShouldReturnClassReps()
    {

        Pageable pageable = PageRequest.of(0, 10);
        Page<User> repsPage = new PageImpl<>(List.of(classRepUser));

        when(userRepository.findByRole(Role.CLASS_REP, pageable)).thenReturn(repsPage);

        Page<User> result = userService.getClassRepsWithBalances(pageable);

        assertThat(result).isEqualTo(repsPage);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRole()).isEqualTo(Role.CLASS_REP);
        verify(userRepository).findByRole(Role.CLASS_REP, pageable);
    }

    @Test
    @DisplayName("clearRepBalance: Should reset balance to zero for class rep")
    void clearRepBalanceShouldResetBalanceToZero()
    {

        when(userRepository.findById(classRepUser.getId())).thenReturn(Optional.of(classRepUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.clearRepBalance(classRepUser.getId());

        assertThat(classRepUser.getCollectedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(userRepository).save(classRepUser);
    }

    @Test
    @DisplayName("clearRepBalance: Should throw exception if user is not a class rep")
    void clearRepBalanceShouldThrowExceptionIfNotClassRep()
    {

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.clearRepBalance(testUser.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not a class representative");
    }

    @Test
    @DisplayName("uploadProfilePicture: Should upload new picture and update user")
    void uploadProfilePictureShouldUploadAndUpdateUser()
    {

        MultipartFile mockFile = mock(MultipartFile.class);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(uploadService.upload(mockFile)).thenReturn("new-avatar-url.jpg");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.uploadProfilePicture(mockFile);

        assertThat(result.getAvatarUrl()).isEqualTo("new-avatar-url.jpg");
        verify(uploadService).upload(mockFile);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("uploadProfilePicture: Should delete old picture if exists")
    void uploadProfilePictureShouldDeleteOldPicture()
    {

        MultipartFile mockFile = mock(MultipartFile.class);
        testUser.setAvatarUrl("old-avatar-url.jpg");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(uploadService.upload(mockFile)).thenReturn("new-avatar-url.jpg");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.uploadProfilePicture(mockFile);

        assertThat(result.getAvatarUrl()).isEqualTo("new-avatar-url.jpg");
        verify(uploadService).delete("old-avatar-url.jpg");
        verify(uploadService).upload(mockFile);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("deleteUser: Should delete user and avatar if exists")
    void deleteUserShouldDeleteUserAndAvatar()
    {

        testUser.setAvatarUrl("avatar-url.jpg");

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        userService.deleteUser(testUser.getId());

        verify(uploadService).delete("avatar-url.jpg");
        verify(userRepository).delete(testUser);
    }

    @Test
    @DisplayName("filterUsers: Should apply multiple filters correctly")
    void filterUsersShouldApplyMultipleFilters()
    {

        List<Role> roles = List.of(Role.USER, Role.CLASS_REP);
        String searchTerm = "test";
        Integer gradYear = 2023;
        BigDecimal balanceEq = BigDecimal.valueOf(100);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(testUser));

        when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(userPage);

        Page<User> result = userService.filterUsers(roles, searchTerm, gradYear, balanceEq, null, null, true, pageable);

        assertThat(result).isEqualTo(userPage);
        verify(userRepository).findAll(any(Specification.class), eq(pageable));
    }
}

