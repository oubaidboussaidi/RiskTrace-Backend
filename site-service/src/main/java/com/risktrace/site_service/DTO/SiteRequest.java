package com.risktrace.site_service.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SiteRequest {
    @NotBlank(message = "siteName is required")
    private String siteName;

    @NotBlank(message = "domain is required")
    private String domain;

    @NotBlank(message = "organizationId is required")
    private String organizationId;
}
