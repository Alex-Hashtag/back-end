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
 * Service class for managing users.
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
     * Retrieves all users with pagination.
     *
     * @param pageable Pagination parameters
     * @return A page of users
     */
    public Page<User> getAllUsers(Pageable pageable)
    {
        return userRepository.findAll(pageable);
    }

    /**
     * Retrieves a user by their ID.
     *
     * @param id The user's ID
     * @return The user
     * @throws UserNotFoundException if the user is not found
     */
    public User getUserById(Long id)
    {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
    }

    /**
     * Gets the currently authenticated user.
     *
     * @return The currently authenticated user
     * @throws UserNotFoundException if the user is not found
     * @throws IllegalStateException if the principal type is invalid
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
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        }
        throw new IllegalStateException("Invalid principal type in security context");
    }

    /**
     * Updates the current user's details.
     *
     * @param userDetails The updated user details
     * @return The updated user
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
     * Updates a user's role.
     *
     * @param userId  The ID of the user to update
     * @param newRole The new role to assign
     * @return The updated user
     * @throws AccessDeniedException if the current user doesn't have permission to update the role
     */
    public User updateUserRole(Long userId, Role newRole)
    {
        User currentUser = getCurrentUser();  // the STUCO or ADMIN performing the update
        User targetUser = getUserById(userId);

        int targetUserRank = getRoleRank(targetUser.getRole());
        int newRoleRank = getRoleRank(newRole);

        if (currentUser.getRole() == Role.STUCO)
        {
            if (targetUserRank >= 2)
            {
                throw new AccessDeniedException("STUCO cannot update users with STUCO or ADMIN role.");
            }
            if (newRoleRank >= 2)
            {
                throw new AccessDeniedException("STUCO can only assign USER or CLASS_REP.");
            }
        }
        else if (currentUser.getRole() == Role.ADMIN)
        {
            if (targetUserRank > 3)
            {
                throw new AccessDeniedException("ADMIN cannot update roles above ADMIN.");
            }
            if (newRoleRank > 3)
            {
                throw new AccessDeniedException("Cannot assign a role above ADMIN.");
            }
        }
        else
        {
            throw new AccessDeniedException("Only STUCO or ADMIN can change roles.");
        }

        targetUser.setRole(newRole);
        return userRepository.save(targetUser);
    }

    /**
     * Retrieves class representatives with their collected balances.
     *
     * @param pageable Pagination parameters
     * @return A page of class representatives
     */
    public Page<User> getClassRepsWithBalances(Pageable pageable)
    {
        return userRepository.findByRole(Role.CLASS_REP, pageable);
    }

    /**
     * Clears a user's collected balance.
     *
     * @param userId The ID of the user
     */
    public void clearUserBalance(Long userId)
    {
        User user = getUserById(userId);
        user.setCollectedBalance(BigDecimal.ZERO);
        userRepository.save(user);
    }

    /**
     * Increments a user's collected balance.
     *
     * @param userId The ID of the user whose balance to increment
     * @param amount The amount to add to the balance
     * @return The updated user
     */
    @Transactional
    public User incrementCollectedBalance(Long userId, BigDecimal amount)
    {
        User user = getUserById(userId);
        BigDecimal currentBalance = user.getCollectedBalance();

        if (currentBalance == null)
        {
            currentBalance = BigDecimal.ZERO;
        }

        user.setCollectedBalance(currentBalance.add(amount));
        return userRepository.save(user);
    }

    /**
     * Uploads a profile picture for the current user.
     *
     * @param file The profile picture file
     * @return The updated user
     */
    @Transactional
    public User uploadProfilePicture(MultipartFile file)
    {
        User user = getCurrentUser();
        String oldAvatarUrl = user.getAvatarUrl();

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

        String newImageUrl = uploadService.upload(file);
        user.setAvatarUrl(newImageUrl);
        return userRepository.save(user);
    }

    /**
     * Deletes a user.
     *
     * @param userId The ID of the user to delete
     */
    @Transactional
    public void deleteUser(Long userId)
    {
        User user = getUserById(userId);

        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty())
        {
            try
            {
                uploadService.delete(user.getAvatarUrl());
            } catch (RuntimeException ex)
            {
                // Log error but continue with deletion
            }
        }
        userRepository.delete(user);
    }

    /**
     * Filters users based on various criteria.
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
    public Page<User> filterUsers(List<Role> roles,
                                  String searchTerm,
                                  Integer graduationYear,
                                  BigDecimal balanceEq,
                                  BigDecimal balanceGt,
                                  BigDecimal balanceLt,
                                  Boolean activeOrders,
                                  Pageable pageable)
    {
        Specification<User> spec = Specification.where(null);

        if (roles != null && !roles.isEmpty())
        {
            spec = spec.and(UserSpecifications.hasRoleIn(roles));
        }

        if (searchTerm != null && !searchTerm.trim().isEmpty())
        {
            spec = spec.and(UserSpecifications.matchesSearchTerm(searchTerm.trim()));
        }

        if (graduationYear != null)
        {
            spec = spec.and(UserSpecifications.hasGraduationYear(graduationYear));
        }

        if (balanceEq != null)
        {
            spec = spec.and(UserSpecifications.hasBalanceEqual(balanceEq));
        }

        if (balanceGt != null)
        {
            spec = spec.and(UserSpecifications.hasBalanceGreaterThan(balanceGt));
        }

        if (balanceLt != null)
        {
            spec = spec.and(UserSpecifications.hasBalanceLessThan(balanceLt));
        }

        if (activeOrders != null && activeOrders)
        {
            spec = spec.and(UserSpecifications.withActiveOrders());
        }

        return userRepository.findAll(spec, pageable);
    }

    /**
     * Gets the numeric rank of a role.
     *
     * @param role The role
     * @return The role's rank
     */
    private int getRoleRank(Role role)
    {
        return switch (role)
        {
            case USER -> 0;
            case CLASS_REP -> 1;
            case STUCO -> 2;
            case ADMIN -> 3;
        };
    }
}
