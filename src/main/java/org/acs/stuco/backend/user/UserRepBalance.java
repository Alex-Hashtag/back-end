package org.acs.stuco.backend.user;

import java.math.BigDecimal;


public record UserRepBalance(
        Long userId,
        String fullName,
        BigDecimal collectedBalance
)
{
}

