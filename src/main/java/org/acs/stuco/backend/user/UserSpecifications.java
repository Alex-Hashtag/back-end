package org.acs.stuco.backend.user;

import jakarta.persistence.criteria.Subquery;
import org.acs.stuco.backend.order.Order;
import org.acs.stuco.backend.order.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

/// # User Specifications
/// 
/// This class provides a collection of JPA Specifications for filtering users based on various criteria.
/// These specifications can be combined to create complex queries using the Spring Data JPA Specification pattern.
/// 
/// ## Usage Example
/// 
/// ```java
/// Specification<User> spec = Specification.where(null);
/// 
/// // Add role filter
/// if (roles != null && !roles.isEmpty()) {
///     spec = spec.and(UserSpecifications.hasRoleIn(roles));
/// }
/// 
/// // Add search term filter
/// if (searchTerm != null && !searchTerm.trim().isEmpty()) {
///     spec = spec.and(UserSpecifications.matchesSearchTerm(searchTerm));
/// }
/// 
/// // Execute the query
/// Page<User> result = userRepository.findAll(spec, pageable);
/// ```
public class UserSpecifications {

    /// Filters users by their roles.
    /// 
    /// @param roles - A list of roles to include in the filter
    /// @return A specification that matches users with any of the specified roles
    public static Specification<User> hasRoleIn(List<Role> roles) {
        return (root, query, cb) -> root.get("role").in(roles);
    }

    /// Searches users by name or email.
    /// 
    /// This performs a case-insensitive partial match on both the name and email fields.
    /// 
    /// @param searchTerm - The term to search for in user names and emails
    /// @return A specification that matches users whose name or email contains the search term
    public static Specification<User> matchesSearchTerm(String searchTerm) {
        return (root, query, cb) -> {
            String likePattern = "%" + searchTerm.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), likePattern),
                    cb.like(cb.lower(root.get("email")), likePattern)
            );
        };
    }

    /// Filters users by their graduation year based on email address pattern.
    /// 
    /// ## Email Format Examples
    /// - a.mihaylov25@acsbg.org
    /// - m.n.georgiev27@acsbg.org
    /// - t.zornitza29@acsbg.org
    /// 
    /// @param year - The two-digit graduation year (e.g., 25 for 2025)
    /// @return A specification that matches users with the specified graduation year in their email
    public static Specification<User> hasGraduationYear(Integer year) {
        return (root, query, cb) -> {
            // Format the year as a two-digit string (e.g., 25, 27, 29)
            String target = String.format("%02d", year);
            
            // Match any email that has the graduation year right before @acsbg.org
            return cb.like(root.get("email"), "%" + target + "@acsbg.org");
        };
    }

    /// Filters users by exact balance amount.
    /// 
    /// @param balance - The exact balance amount to match
    /// @return A specification that matches users with exactly the specified balance
    public static Specification<User> hasBalanceEqual(BigDecimal balance) {
        return (root, query, cb) -> cb.equal(root.get("collectedBalance"), balance);
    }

    /// Filters users with balance greater than the specified amount.
    /// 
    /// @param balance - The minimum balance threshold
    /// @return A specification that matches users with balance greater than the specified amount
    public static Specification<User> hasBalanceGreaterThan(BigDecimal balance) {
        return (root, query, cb) -> cb.greaterThan(root.get("collectedBalance"), balance);
    }

    /// Filters users with balance less than the specified amount.
    /// 
    /// @param balance - The maximum balance threshold
    /// @return A specification that matches users with balance less than the specified amount
    public static Specification<User> hasBalanceLessThan(BigDecimal balance) {
        return (root, query, cb) -> cb.lessThan(root.get("collectedBalance"), balance);
    }

    /// Filters users who have active orders (PENDING or PAID status).
    /// 
    /// This specification excludes users who only have DELIVERED or CANCELLED orders.
    /// It uses a subquery to check for the existence of active orders for each user.
    /// 
    /// @return A specification that matches users with at least one active order
    public static Specification<User> withActiveOrders() {
        return (root, query, cb) -> {
            // Prevent duplicate results in count queries
            query.distinct(true);
            
            Subquery<Long> subquery = query.subquery(Long.class);
            var orderRoot = subquery.from(Order.class);

            subquery.select(orderRoot.get("id"));

            // Only consider PENDING and PAID as active orders
            subquery.where(
                    cb.equal(orderRoot.get("buyer").get("id"), root.get("id")),
                    orderRoot.get("status").in(OrderStatus.PENDING, OrderStatus.PAID)
            );

            return cb.exists(subquery);
        };
    }
}
