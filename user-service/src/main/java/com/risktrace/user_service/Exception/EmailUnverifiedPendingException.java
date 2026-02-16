package com.risktrace.user_service.Exception;

public class EmailUnverifiedPendingException extends RuntimeException {
    public EmailUnverifiedPendingException(String message) {
        super(message);
    }
}
