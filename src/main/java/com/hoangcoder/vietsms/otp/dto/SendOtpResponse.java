package com.hoangcoder.vietsms.otp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendOtpResponse(
        Long otpId,
        String phone,
        Integer length,
        Instant expiresAt,
        String devCode
) {}
