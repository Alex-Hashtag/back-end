package org.acs.stuco.backend.user;

import jakarta.persistence.criteria.Subquery;
import org.acs.stuco.backend.order.Order;
import org.acs.stuco.backend.order.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

/**
 * Specifications for the User entity.
 */
public class UserSpecifications
{

    public static Specification<User> hasRoleIn(List<Role> roles)
    {
        return (root, query, cb) -> root.get("role").in(roles);
    }

    public static Specification<User> matchesSearchTerm(String searchTerm)
    {
        return (root, query, cb) ->
        {
            String likePattern = "%" + searchTerm.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), likePattern),
                    cb.like(cb.lower(root.get("email")), likePattern)
            );
        };
    }

    /**
     * Creates a specification to filter users by their graduation year.
     * Matches emails that end with the graduation year followed by @acsbg.org
     * Examples: a.mihaylov25@acsbg.org, m.n.georgiev27@acsbg.org, t.zornitza29@acsbg.org
     *
     * @param year The two-digit graduation year (e.g., 25 for 2025)
     * @return A specification that filters users by graduation year
     */
    public static Specification<User> hasGraduationYear(Integer year)
    {
        return (root, query, cb) ->
        {
            // Format the year as a two-digit string (e.g., 25, 27, 29)
            String target = String.format("%02d", year);
            
            // Match any email that has the graduation year right before @acsbg.org
            // This handles various formats like:
            // a.mihaylov25@acsbg.org
            // m.n.georgiev27@acsbg.org
            // t.zornitza29@acsbg.org
            return cb.like(root.get("email"), "%" + target + "@acsbg.org");
        };
    }

    public static Specification<User> hasBalanceEqual(BigDecimal balance)
    {
        return (root, query, cb) -> cb.equal(root.get("collectedBalance"), balance);
    }

    public static Specification<User> hasBalanceGreaterThan(BigDecimal balance)
    {
        return (root, query, cb) -> cb.greaterThan(root.get("collectedBalance"), balance);
    }

    public static Specification<User> hasBalanceLessThan(BigDecimal balance)
    {
        return (root, query, cb) -> cb.lessThan(root.get("collectedBalance"), balance);
    }

    /**
     * Specification to find users who have active orders.
     * Active orders are those with status PENDING or PAID.
     * 
     * @return Specification that filters users with active orders
     */
    public static Specification<User> withActiveOrders()
    {
        return (root, query, cb) ->
        {
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
