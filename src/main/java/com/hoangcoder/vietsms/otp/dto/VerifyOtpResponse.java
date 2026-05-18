package com.hoangcoder.vietsms.otp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerifyOtpResponse(
        boolean verified,
        String reason,
        Integer attemptsLeft
) {
    public static VerifyOtpResponse ok() {
        return new VerifyOtpResponse(true, null, null);
    }

    public static VerifyOtpResponse fail(String reason, Integer attemptsLeft) {
        return new VerifyOtpResponse(false, reason, attemptsLeft);
    }
}
