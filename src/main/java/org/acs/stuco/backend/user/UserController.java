package org.acs.stuco.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;


/**
 * REST controller handling user management operations.
 */
@RestController
@RequestMapping("/api/users")
public class UserController
{
    private final UserService userService;

    public UserController(UserService userService)
    {
        this.userService = userService;
    }

    /// GET /api/users
    /// Retrieves paginated list of all users (STUCO/Admin only).
    ///
    /// @param pageable Pagination parameters (page, size, sort)
    /// @return 200 OK with page of users
    @GetMapping
    @PreAuthorize("hasRole('REP') or hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Page<User>> getAllUsers(Pageable pageable)
    {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    /// GET /api/users/{id}
    /// Retrieves a specific user by ID (STUCO/Admin only).
    ///
    /// @param id User ID to retrieve
    /// @return 200 OK with user entity
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('REP') or hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<User> getUserById(@PathVariable Long id)
    {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /// GET /api/users/me
    /// Retrieves the currently authenticated user.
    ///
    /// @return 200 OK with user entity
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser()
    {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    /// PUT /api/users/me
    /// Updates basic information for the current user.
    ///
    /// @param userDetails User object containing updated name
    /// @return 200 OK with updated user entity
    @PutMapping("/me")
    public ResponseEntity<User> updateCurrentUser(@RequestBody User userDetails)
    {
        return ResponseEntity.ok(userService.updateCurrentUser(userDetails));
    }

    /// PATCH /api/users/{id}/role
    /// Updates a user's role (Admin only).
    ///
    /// @param id      User ID to modify
    /// @param newRole New role (from request parameter)
    /// @return 200 OK with updated user entity
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('STUCO','ADMIN')")
    public ResponseEntity<User> updateUserRole(
            @PathVariable Long id,
            @RequestParam Role newRole)
    {
        return ResponseEntity.ok(userService.updateUserRole(id, newRole));
    }


    /// GET /api/users/roles
    /// Retrieves list of available user roles (STUCO/Admin only).
    ///
    /// @return 200 OK with array of roles
    @GetMapping("/roles")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Role[]> getAllRoles()
    {
        return ResponseEntity.ok(Role.values());
    }

    /// GET /api/users/class-reps
    /// Retrieves paginated list of class representatives with balances (STUCO/Admin only).
    ///
    /// @param pageable Pagination parameters
    /// @return 200 OK with page of class representatives
    @GetMapping("/class-reps")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Page<User>> getClassRepsWithBalances(Pageable pageable)
    {
        return ResponseEntity.ok(userService.getClassRepsWithBalances(pageable));
    }

    /// POST /api/users/{id}/balance/clear
    /// Resets a class representative's balance to zero (STUCO/Admin only).
    ///
    /// @param id ID of class representative
    /// @return 204 No Content
    @PostMapping("/{id}/balance/clear")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Void> clearRepBalance(@PathVariable Long id)
    {
        userService.clearRepBalance(id);
        return ResponseEntity.noContent().build();
    }

    /// POST /api/users/upload-pfp
    /// Uploads a new profile picture for the current user.
    ///
    /// @param file Image file (multipart/form-data)
    /// @return 200 OK with updated user entity
    @PostMapping("/upload-pfp")
    public ResponseEntity<User> uploadProfilePicture(
            @RequestParam("file") MultipartFile file)
    {
        return ResponseEntity.ok(userService.uploadProfilePicture(file));
    }

    /// DELETE /api/users/{id}
    /// Deletes a user and their profile picture (Admin only).
    ///
    /// @param id ID of user to delete
    /// @return 204 No Content
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id)
    {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('REP') or hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Page<User>> filterUsers(
            @RequestParam(required = false) List<Role> roles,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) Integer graduationYear,
            @RequestParam(required = false) BigDecimal balanceEq,
            @RequestParam(required = false) BigDecimal balanceGt,
            @RequestParam(required = false) BigDecimal balanceLt,
            @RequestParam(required = false) Boolean activeOrders,
            Pageable pageable
    ) {
        Page<User> result = userService.filterUsers(roles, searchTerm, graduationYear,
                balanceEq, balanceGt, balanceLt, activeOrders, pageable);
        return ResponseEntity.ok(result);
    }
}