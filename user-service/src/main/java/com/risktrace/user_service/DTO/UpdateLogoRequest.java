package com.risktrace.user_service.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLogoRequest {
    /** Base64-encoded data URI of the organization logo, e.g. "data:image/png;base64,..." */
    private String imageDataUrl;
}
