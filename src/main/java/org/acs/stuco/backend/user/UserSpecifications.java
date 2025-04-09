package org.acs.stuco.backend.user;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import org.acs.stuco.backend.order.Order;
import org.acs.stuco.backend.order.OrderStatus;
import org.springframework.data.jpa.domain.Specification;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.List;

public class UserSpecifications {

    /**
     * Filter by multiple roles.
     * e.g. roles=[ADMIN, STUCO]
     */
    public static Specification<User> hasRoleIn(List<Role> roles) {
        return (root, query, cb) -> root.get("role").in(roles);
    }

    /**
     * Case-insensitive "vague" search that matches name or email.
     */
    public static Specification<User> matchesSearchTerm(String searchTerm) {
        return (root, query, cb) -> {
            String likePattern = "%" + searchTerm.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), likePattern),
                    cb.like(cb.lower(root.get("email")), likePattern)
            );
        };
    }

    /**
     * Filter by graduation year (last 2 digits before '@acsbg.org').
     *
     * For an email like "john.doe25@acsbg.org":
     *    - '25' is the year
     *    - We check for something that ends with ".25@acsbg.org"
     */
    public static Specification<User> hasGraduationYear(Integer year) {
        return (root, query, cb) -> {
            // Format year (e.g. 25 -> "25")
            String target = String.format("%02d", year);
            // Match "...25@acsbg.org".
            // Using the '.' so that it ensures it’s after a name segment.
            return cb.like(root.get("email"), "%." + target + "@acsbg.org");
        };
    }

    // New: Balance equality filter
    public static Specification<User> hasBalanceEqual(BigDecimal balance) {
        return (root, query, cb) -> cb.equal(root.get("collectedBalance"), balance);
    }

    // New: Balance greater-than filter
    public static Specification<User> hasBalanceGreaterThan(BigDecimal balance) {
        return (root, query, cb) -> cb.greaterThan(root.get("collectedBalance"), balance);
    }

    // New: Balance less-than filter
    public static Specification<User> hasBalanceLessThan(BigDecimal balance) {
        return (root, query, cb) -> cb.lessThan(root.get("collectedBalance"), balance);
    }

    /**
     * Filters users to only those who have at least one active order.
     * "Active" orders are defined here as orders with a status of PAID or DELIVERED.
     */
    public static Specification<User> withActiveOrders() {
        return (root, query, cb) -> {
            // Create a subquery on the Order entity.
            Subquery<Long> subquery = query.subquery(Long.class);
            var orderRoot = subquery.from(Order.class);
            // Select the order id
            subquery.select(orderRoot.get("id"));
            // Where the order buyer is the user in question and
            // order status is either PAID or DELIVERED.
            subquery.where(
                    cb.equal(orderRoot.get("buyer").get("id"), root.get("id")),
                    orderRoot.get("status").in(OrderStatus.PAID, OrderStatus.DELIVERED)
            );
            // Only include users for which the subquery yields a result.
            return cb.exists(subquery);
        };
    }
}
