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
 * Controller for handling user-related operations.
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

    /**
     * Retrieves all users with pagination (requires REP, STUCO, or ADMIN role).
     *
     * @param pageable Pagination parameters
     * @return A page of users
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('REP', 'STUCO', 'ADMIN')")
    public ResponseEntity<Page<User>> getAllUsers(Pageable pageable)
    {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    /**
     * Retrieves a specific user by ID (requires REP, STUCO, or ADMIN role).
     *
     * @param id The user's ID
     * @return The user
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('REP', 'STUCO', 'ADMIN')")
    public ResponseEntity<User> getUserById(@PathVariable Long id)
    {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Retrieves the currently authenticated user.
     *
     * @return The currently authenticated user
     */
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser()
    {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    /**
     * Updates the currently authenticated user's details.
     *
     * @param userDetails The updated details
     * @return The updated user
     */
    @PutMapping("/me")
    public ResponseEntity<User> updateCurrentUser(@RequestBody User userDetails)
    {
        return ResponseEntity.ok(userService.updateCurrentUser(userDetails));
    }

    /**
     * Updates a user's role (requires STUCO or ADMIN role).
     *
     * @param id      The ID of the user to update
     * @param newRole The new role to assign
     * @return The updated user
     */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('STUCO','ADMIN')")
    public ResponseEntity<User> updateUserRole(
            @PathVariable Long id,
            @RequestParam Role newRole)
    {
        return ResponseEntity.ok(userService.updateUserRole(id, newRole));
    }

    /**
     * Retrieves all available roles (requires STUCO or ADMIN role).
     *
     * @return All available roles
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('STUCO', 'ADMIN')")
    public ResponseEntity<Role[]> getAllRoles()
    {
        return ResponseEntity.ok(Role.values());
    }

    /**
     * Retrieves all class representatives with their collected balances (requires STUCO or ADMIN role).
     *
     * @param pageable Pagination parameters
     * @return A page of class representatives
     */
    @GetMapping("/class-reps")
    @PreAuthorize("hasAnyRole('STUCO', 'ADMIN')")
    public ResponseEntity<Page<User>> getClassRepsWithBalances(Pageable pageable)
    {
        return ResponseEntity.ok(userService.getClassRepsWithBalances(pageable));
    }

    /**
     * Clears a user's collected balance (requires STUCO or ADMIN role).
     *
     * @param id The ID of the user
     * @return No content
     */
    @PostMapping("/{id}/balance/clear")
    @PreAuthorize("hasAnyRole('STUCO', 'ADMIN')")
    public ResponseEntity<Void> clearRepBalance(@PathVariable Long id)
    {
        userService.clearUserBalance(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Uploads a profile picture for the currently authenticated user.
     *
     * @param file The profile picture file
     * @return The updated user
     */
    @PostMapping("/upload-pfp")
    public ResponseEntity<User> uploadProfilePicture(
            @RequestParam("file") MultipartFile file)
    {
        return ResponseEntity.ok(userService.uploadProfilePicture(file));
    }

    /**
     * Deletes a user (requires STUCO or ADMIN role).
     *
     * @param id The ID of the user to delete
     * @return No content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUCO', 'ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id)
    {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Filters users based on various criteria (requires REP, STUCO, or ADMIN role).
     *
     * @param roles          The roles to filter by
     * @param searchTerm     The search term to filter by
     * @param graduationYear The graduation year to filter by
     * @param balanceEq      The exact balance to filter by
     * @param balanceGt      The minimum balance to filter by
     * @param balanceLt      The maximum balance to filter by
     * @param activeOrders   Whether to filter by active orders
     * @param pageable       Pagination parameters
     * @return A page of filtered users
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('REP', 'STUCO', 'ADMIN')")
    public ResponseEntity<Page<User>> filterUsers(
            @RequestParam(required = false) List<Role> roles,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) Integer graduationYear,
            @RequestParam(required = false) BigDecimal balanceEq,
            @RequestParam(required = false) BigDecimal balanceGt,
            @RequestParam(required = false) BigDecimal balanceLt,
            @RequestParam(required = false) Boolean activeOrders,
            Pageable pageable)
    {
        Page<User> result = userService.filterUsers(roles, searchTerm, graduationYear,
                balanceEq, balanceGt, balanceLt, activeOrders, pageable);
        return ResponseEntity.ok(result);
    }
}
