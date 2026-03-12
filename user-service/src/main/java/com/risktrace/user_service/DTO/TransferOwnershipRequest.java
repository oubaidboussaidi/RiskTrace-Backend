package com.risktrace.user_service.DTO;

import lombok.Data;

@Data
public class TransferOwnershipRequest {
    private String newOwnerUserId;
}
