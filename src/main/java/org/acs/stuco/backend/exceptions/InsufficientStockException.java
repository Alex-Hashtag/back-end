package org.acs.stuco.backend.exceptions;

public class InsufficientStockException extends RuntimeException
{
    public InsufficientStockException()
    {
        super("Not enough stock available for this product");
    }
}

