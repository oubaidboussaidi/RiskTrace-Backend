package com.risktrace.user_service.DTO;

import com.risktrace.user_service.Enums.OrganizationRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMemberResponse {
    private String id;
    private String userId;
    private String email;
    private String fullName;
    private String organizationId;
    private OrganizationRole role;
    private Instant createdAt;
}
