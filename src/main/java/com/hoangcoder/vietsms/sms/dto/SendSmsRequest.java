package com.hoangcoder.vietsms.sms.dto;

import com.hoangcoder.vietsms.validation.VietnamesePhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendSmsRequest(
        @Schema(description = "Recipient phone number in Vietnamese format",
                example = "0987654321")
        @VietnamesePhone
        String to,

        @Schema(description = "SMS body, max 160 chars",
                example = "Ma OTP cua ban la 123456")
        @NotBlank
        @Size(max = 160, message = "content must be at most 160 characters")
        String content,

        @Schema(description = "Client-chosen idempotency key. Resending with the same value returns the original message.",
                example = "order-12345")
        @Size(max = 64, message = "client_message_id must be at most 64 characters")
        String clientMessageId
) {}
