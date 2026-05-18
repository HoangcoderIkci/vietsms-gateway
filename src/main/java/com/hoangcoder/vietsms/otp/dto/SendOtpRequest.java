package com.hoangcoder.vietsms.otp.dto;

import com.hoangcoder.vietsms.validation.VietnamesePhone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SendOtpRequest(
        @VietnamesePhone
        String phone,

        @Min(value = 4, message = "length must be between 4 and 8")
        @Max(value = 8, message = "length must be between 4 and 8")
        Integer length,

        @Min(value = 30, message = "ttl_seconds must be between 30 and 900")
        @Max(value = 900, message = "ttl_seconds must be between 30 and 900")
        Integer ttlSeconds
) {}
