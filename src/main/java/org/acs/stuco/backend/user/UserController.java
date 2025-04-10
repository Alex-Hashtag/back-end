package org.acs.stuco.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;


@RestController
@RequestMapping("/api/users")
public class UserController
{
    private final UserService userService;

    public UserController(UserService userService)
    {
        this.userService = userService;
    }


    @GetMapping
    @PreAuthorize("hasRole('REP') or hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Page<User>> getAllUsers(Pageable pageable)
    {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasRole('REP') or hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<User> getUserById(@PathVariable Long id)
    {
        return ResponseEntity.ok(userService.getUserById(id));
    }


    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser()
    {
        return ResponseEntity.ok(userService.getCurrentUser());
    }


    @PutMapping("/me")
    public ResponseEntity<User> updateCurrentUser(@RequestBody User userDetails)
    {
        return ResponseEntity.ok(userService.updateCurrentUser(userDetails));
    }


    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('STUCO','ADMIN')")
    public ResponseEntity<User> updateUserRole(
            @PathVariable Long id,
            @RequestParam Role newRole)
    {
        return ResponseEntity.ok(userService.updateUserRole(id, newRole));
    }


    @GetMapping("/roles")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Role[]> getAllRoles()
    {
        return ResponseEntity.ok(Role.values());
    }


    @GetMapping("/class-reps")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Page<User>> getClassRepsWithBalances(Pageable pageable)
    {
        return ResponseEntity.ok(userService.getClassRepsWithBalances(pageable));
    }


    @PostMapping("/{id}/balance/clear")
    @PreAuthorize("hasRole('STUCO') or hasRole('ADMIN')")
    public ResponseEntity<Void> clearRepBalance(@PathVariable Long id)
    {
        userService.clearRepBalance(id);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/upload-pfp")
    public ResponseEntity<User> uploadProfilePicture(
            @RequestParam("file") MultipartFile file)
    {
        return ResponseEntity.ok(userService.uploadProfilePicture(file));
    }


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
    )
    {
        Page<User> result = userService.filterUsers(roles, searchTerm, graduationYear,
                balanceEq, balanceGt, balanceLt, activeOrders, pageable);
        return ResponseEntity.ok(result);
    }
}
