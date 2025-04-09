package org.acs.stuco.backend.user;

import org.acs.stuco.backend.exceptions.UserNotFoundException;
import org.acs.stuco.backend.upload.UploadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;


/**
 * Service class handling user-related operations including CRUD, role management, and profile picture handling.
 */
@Service
public class UserService
{
    private final UserRepository userRepository;
    private final UploadService uploadService;

    public UserService(UserRepository userRepository, UploadService uploadService)
    {
        this.userRepository = userRepository;
        this.uploadService = uploadService;
    }

    /**
     * Retrieves a paginated list of all users.
     *
     * @param pageable Pagination configuration
     * @return Page of users
     */
    public Page<User> getAllUsers(Pageable pageable)
    {
        return userRepository.findAll(pageable);
    }

    /**
     * Finds a user by their ID.
     *
     * @param id User ID to search for
     * @return Found user entity
     * @throws UserNotFoundException if no user exists with the given ID
     */
    public User getUserById(Long id)
    {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
    }

    /**
     * Retrieves the currently authenticated user from the security context.
     *
     * @return Authenticated user entity
     * @throws UserNotFoundException if no user is authenticated or user doesn't exist
     */
    public User getCurrentUser()
    {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User userPrincipal)
        {
            String email = userPrincipal.getEmail();
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        }
        else if (principal instanceof String email)
        {
            // If your setup sometimes stores the username as a string, handle it here.
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        }
        throw new IllegalStateException("Invalid principal type in security context");
    }


    /**
     * Updates basic information for the currently authenticated user.
     *
     * @param userDetails User entity containing updated fields (only name is updatable)
     * @return Updated user entity
     */
    public User updateCurrentUser(User userDetails)
    {
        User currentUser = getCurrentUser();
        if (userDetails.getName() != null)
        {
            currentUser.setName(userDetails.getName());
        }
        return userRepository.save(currentUser);
    }

    /**
     * Updates the role of a specific user (admin-only operation).
     *
     * @param userId  ID of user to modify
     * @param newRole New role to assign
     * @return Updated user entity
     */
    public User updateUserRole(Long userId, Role newRole) {
        User currentUser = getCurrentUser();  // the STUCO or ADMIN performing the update
        User targetUser  = getUserById(userId);

        int targetUserRank  = getRoleRank(targetUser.getRole());
        int newRoleRank     = getRoleRank(newRole);

        // If current user is STUCO (rank 3) ...
        if (currentUser.getRole() == Role.STUCO) {
            // STUCO can only update users whose role is strictly below STUCO (rank < 3)
            // and can only assign roles strictly below STUCO (rank < 3).
            if (targetUserRank >= 2) {
                throw new AccessDeniedException("STUCO cannot update users with STUCO or ADMIN role.");
            }
            if (newRoleRank >= 2) {
                throw new AccessDeniedException("STUCO can only assign USER or CLASS_REP.");
            }
        }
        // If current user is ADMIN (rank 4) ...
        else if (currentUser.getRole() == Role.ADMIN) {
            // ADMIN can update any user rank <= 4 (which is everyone)
            // but if you want to block future roles above ADMIN, we can check that too.
            if (targetUserRank > 3) {
                throw new AccessDeniedException("ADMIN cannot update roles above ADMIN.");
            }
            if (newRoleRank > 3) {
                throw new AccessDeniedException("Cannot assign a role above ADMIN.");
            }
        }
        // Otherwise, user is neither STUCO nor ADMIN => not authorized
        else {
            throw new AccessDeniedException("Only STUCO or ADMIN can change roles.");
        }

        // If all checks pass, update the role
        targetUser.setRole(newRole);
        return userRepository.save(targetUser);
    }

    /**
     * Retrieves paginated list of class representatives with their collected balances.
     *
     * @param pageable Pagination configuration
     * @return Page of class representatives
     */
    public Page<User> getClassRepsWithBalances(Pageable pageable)
    {
        return userRepository.findByRole(Role.CLASS_REP, pageable);
    }

    /**
     * Resets the collected balance of a class representative to zero.
     *
     * @param repId ID of the class representative
     * @throws AccessDeniedException if the user is not a class representative
     */
    public void clearRepBalance(Long repId)
    {
        User rep = getUserById(repId);
        if (rep.getRole() != Role.CLASS_REP)
        {
            throw new AccessDeniedException("User is not a class representative");
        }
        rep.setCollectedBalance(BigDecimal.ZERO);
        userRepository.save(rep);
    }

    /**
     * Uploads a new profile picture for the current user and deletes the old one if it exists.
     *
     * @param file Image file to upload
     * @return Updated user entity with new avatar URL
     * @throws RuntimeException if file upload fails
     * @Transactional Ensures atomic update of user record and image deletion/upload
     */
    @Transactional
    public User uploadProfilePicture(MultipartFile file)
    {
        User user = getCurrentUser();
        String oldAvatarUrl = user.getAvatarUrl();

        // Delete old profile picture if it exists
        if (oldAvatarUrl != null && !oldAvatarUrl.isEmpty())
        {
            try
            {
                uploadService.delete(oldAvatarUrl);
            } catch (RuntimeException ex)
            {
                throw new RuntimeException("Failed to delete old profile picture: " + oldAvatarUrl, ex);
            }
        }

        // Upload new picture
        String newImageUrl = uploadService.upload(file);
        user.setAvatarUrl(newImageUrl);
        return userRepository.save(user);
    }

    /**
     * Deletes a user and their associated profile picture.
     *
     * @param userId ID of user to delete
     * @throws UserNotFoundException if user doesn't exist
     * @Transactional Ensures atomic deletion of user record and associated image
     */
    @Transactional
    public void deleteUser(Long userId)
    {
        User user = getUserById(userId);
        // Delete profile picture from Cloudinary if exists
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty())
        {
            try
            {
                uploadService.delete(user.getAvatarUrl());
            } catch (RuntimeException ex)
            {
                // Log error but proceed with user deletion
            }
        }
        userRepository.delete(user);
    }

    public Page<User> filterUsers(List<Role> roles,
                                  String searchTerm,
                                  Integer graduationYear,
                                  BigDecimal balanceEq,
                                  BigDecimal balanceGt,
                                  BigDecimal balanceLt,
                                  Boolean activeOrders, // New parameter
                                  Pageable pageable) {
        Specification<User> spec = Specification.where(null);

        // Role filter
        if (roles != null && !roles.isEmpty()) {
            spec = spec.and(UserSpecifications.hasRoleIn(roles));
        }

        // Vague search filter (name or email)
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            spec = spec.and(UserSpecifications.matchesSearchTerm(searchTerm.trim()));
        }

        // Graduation year filter (from email digits)
        if (graduationYear != null) {
            spec = spec.and(UserSpecifications.hasGraduationYear(graduationYear));
        }

        // Balance exact match filter
        if (balanceEq != null) {
            spec = spec.and(UserSpecifications.hasBalanceEqual(balanceEq));
        }

        // Balance greater-than filter
        if (balanceGt != null) {
            spec = spec.and(UserSpecifications.hasBalanceGreaterThan(balanceGt));
        }

        // Balance less-than filter
        if (balanceLt != null) {
            spec = spec.and(UserSpecifications.hasBalanceLessThan(balanceLt));
        }

        // --- NEW: Active orders filter ---
        if (activeOrders != null && activeOrders) {
            spec = spec.and(UserSpecifications.withActiveOrders());
        }

        // Execute the dynamic query with pagination and sorting.
        return userRepository.findAll(spec, pageable);
    }

    private int getRoleRank(Role role) {
        return switch (role) {
            case USER -> 0;
            case CLASS_REP -> 1;
            case STUCO -> 2;
            case ADMIN -> 3;
        };
    }

}
