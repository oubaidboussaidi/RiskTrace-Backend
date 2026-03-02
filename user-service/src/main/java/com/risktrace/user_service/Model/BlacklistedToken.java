package com.risktrace.user_service.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "blacklisted_tokens")
public class BlacklistedToken {
    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    private Instant expiryDate;      // when the token would have naturally expired
    private Instant blacklistedAt;
}
