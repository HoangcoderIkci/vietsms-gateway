package com.hoangcoder.vietsms.otp.dto;

import com.hoangcoder.vietsms.validation.VietnamesePhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SendOtpRequest(
        @Schema(description = "Recipient phone number", example = "0987654321")
        @VietnamesePhone
        String phone,

        @Schema(description = "Code length (default 6). Range 4-8.", example = "6")
        @Min(value = 4, message = "length must be between 4 and 8")
        @Max(value = 8, message = "length must be between 4 and 8")
        Integer length,

        @Schema(description = "Time-to-live in seconds (default 300). Range 30-900.", example = "300")
        @Min(value = 30, message = "ttl_seconds must be between 30 and 900")
        @Max(value = 900, message = "ttl_seconds must be between 30 and 900")
        Integer ttlSeconds
) {}
