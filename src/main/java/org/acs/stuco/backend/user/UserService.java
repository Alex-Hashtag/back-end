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


    public Page<User> getAllUsers(Pageable pageable)
    {
        return userRepository.findAll(pageable);
    }


    public User getUserById(Long id)
    {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
    }


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


    public User updateCurrentUser(User userDetails)
    {
        User currentUser = getCurrentUser();
        if (userDetails.getName() != null)
        {
            currentUser.setName(userDetails.getName());
        }
        return userRepository.save(currentUser);
    }


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


    public Page<User> getClassRepsWithBalances(Pageable pageable)
    {
        return userRepository.findByRole(Role.CLASS_REP, pageable);
    }


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


