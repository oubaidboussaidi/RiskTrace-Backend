package com.risktrace.user_service.DTO;

import com.risktrace.user_service.Enums.OrganizationRole;
import lombok.Data;

@Data
public class InviteMemberRequest {
    private String email;
    private OrganizationRole role;
}
