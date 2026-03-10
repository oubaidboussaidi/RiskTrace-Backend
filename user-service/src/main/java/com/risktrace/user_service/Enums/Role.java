package com.risktrace.user_service.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Role {
    ADMIN,
    USER; // Required to support existing database records and prevent 'No enum constant'
          // crashes

    @JsonCreator
    public static Role fromString(String value) {
        if (value == null)
            return USER;
        try {
            String upper = value.toUpperCase();
            if (upper.equals("PLATFORM_ADMIN"))
                return ADMIN;
            if (upper.equals("ANALYST") || upper.equals("VIEWER"))
                return USER;
            return Role.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return USER; // Default to USER for safety if role is unknown
        }
    }
}
