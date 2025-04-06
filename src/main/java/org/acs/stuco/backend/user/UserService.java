package org.acs.stuco.backend.user;

import org.acs.stuco.backend.exceptions.UserNotFoundException;
import org.acs.stuco.backend.upload.UploadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;


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
    public User updateUserRole(Long userId, Role newRole)
    {
        User user = getUserById(userId);
        user.setRole(newRole);
        return userRepository.save(user);
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
}
