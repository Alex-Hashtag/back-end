package org.acs.stuco.backend.user;

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
}
