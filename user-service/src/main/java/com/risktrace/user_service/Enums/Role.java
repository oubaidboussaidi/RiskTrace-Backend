package com.risktrace.user_service.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    ADMIN,
    USER; // Required to support existing database records and prevent 'No enum constant'
          // crashes

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static Role fromString(String value) {
        if (value == null || value.isBlank())
            return USER;
        try {
            String upper = value.trim().toUpperCase();
            if (upper.equals("PLATFORM_ADMIN") || upper.equals("ADMIN")) {
                return ADMIN;
            }
            if (upper.equals("ANALYST") || upper.equals("VIEWER") || upper.equals("USER")) {
                return USER;
            }
            return Role.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }
}
