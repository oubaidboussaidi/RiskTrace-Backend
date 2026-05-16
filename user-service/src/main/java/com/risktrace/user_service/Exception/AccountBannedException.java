package com.risktrace.user_service.Exception;

public class AccountBannedException extends RuntimeException {
    public AccountBannedException(String message) {
        super(message);
    }
}
