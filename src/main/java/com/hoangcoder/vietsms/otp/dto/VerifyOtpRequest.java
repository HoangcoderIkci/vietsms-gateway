package com.hoangcoder.vietsms.otp.dto;

import com.hoangcoder.vietsms.validation.VietnamesePhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @VietnamesePhone
        String phone,

        @NotBlank
        @Pattern(regexp = "\\d{4,8}", message = "code must be 4-8 digits")
        String code
) {}
