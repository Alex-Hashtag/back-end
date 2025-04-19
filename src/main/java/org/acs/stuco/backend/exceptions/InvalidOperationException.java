package org.acs.stuco.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/// # Invalid Operation Exception
/// 
/// Exception thrown when an operation violates business rules or constraints.
/// This exception is automatically mapped to HTTP 400 (Bad Request) when thrown in controllers.
/// 
/// ## Common Use Cases
/// 
/// - Attempting to change order status in an invalid way
/// - Trying to modify an order assigned to another representative
/// - Attempting to skip order status steps (e.g., PENDING directly to DELIVERED)
/// 
/// ## Example Usage
/// 
/// ```java
/// if (newStatus == OrderStatus.DELIVERED && currentStatus == OrderStatus.PENDING) {
///     throw new InvalidOperationException("Cannot change status directly from PENDING to DELIVERED");
/// }
/// ```
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidOperationException extends RuntimeException {

    /// Creates a new InvalidOperationException with the specified message.
    /// 
    /// @param message - A description of why the operation is invalid
    public InvalidOperationException(String message) {
        super(message);
    }

    /// Creates a new InvalidOperationException with the specified message and cause.
    /// 
    /// @param message - A description of why the operation is invalid
    /// @param cause - The underlying exception that caused this exception
    public InvalidOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
