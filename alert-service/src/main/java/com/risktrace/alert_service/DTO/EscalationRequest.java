package com.risktrace.alert_service.DTO;

import lombok.Data;

@Data
public class EscalationRequest {
    private String message;
    private String analystName;
}
