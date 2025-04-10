package org.acs.stuco.backend.exceptions;

public class EmailSendException extends RuntimeException
{
    public EmailSendException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
