package com.risktrace.user_service.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.risktrace.user_service.Enums.Role;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User implements UserDetails {

    @Id
    private String id;

    @Field("name")
    private String fullName;

    @Indexed(unique = true)
    private String email;

    private String password;

    private Role role;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private boolean emailVerified = false;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Builder.Default
    private int failedLoginAttempts = 0;

    @Builder.Default
    private boolean accountNonLocked = true;

    private Instant lockTime;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Builder.Default
    private boolean isTwoFactorEnabled = false;

    private String twoFactorSecret;

    /** Base64-encoded profile avatar image (data URI), nullable */
    private String profileImageUrl;

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
