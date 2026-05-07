package com.risktrace.user_service.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAvatarRequest {
    /** Base64-encoded data URI of the image, e.g. "data:image/png;base64,..." */
    private String imageDataUrl;
}
