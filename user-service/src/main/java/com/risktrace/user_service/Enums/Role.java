package com.risktrace.user_service.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Role {
    ADMIN,
    ANALYST,
    USER; // Required to support existing database records and prevent 'No enum constant' crashes

    @JsonCreator
    public static Role fromString(String value) {
        if (value == null)
            return ANALYST;
        try {
            return Role.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ANALYST; // Default to ANALYST for safety if role is unknown
        }
    }
}
