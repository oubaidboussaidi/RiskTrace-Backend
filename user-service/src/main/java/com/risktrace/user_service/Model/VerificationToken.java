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
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "verification_tokens")
public class VerificationToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    /** Reference to the user's email (immutable identifier). */
    private String userEmail;

    private Instant expiryDate;

    private Instant createdAt;
}
