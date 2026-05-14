package com.risktrace.user_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.risktrace.user_service.Enums.Role;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private String id;
    private String fullName;
    private String email;
    private Role role;
    private boolean enabled;
    private boolean emailVerified;
    private boolean accountNonLocked;

    @JsonProperty("isTwoFactorEnabled")
    private boolean isTwoFactorEnabled;
    private Instant createdAt;
    private Instant updatedAt;
    private String profileImageUrl;
}
