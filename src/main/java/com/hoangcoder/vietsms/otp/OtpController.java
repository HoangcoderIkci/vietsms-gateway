package com.hoangcoder.vietsms.otp;

import com.hoangcoder.vietsms.otp.dto.SendOtpRequest;
import com.hoangcoder.vietsms.otp.dto.SendOtpResponse;
import com.hoangcoder.vietsms.otp.dto.VerifyOtpRequest;
import com.hoangcoder.vietsms.otp.dto.VerifyOtpResponse;
import com.hoangcoder.vietsms.security.ApiKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/otp")
@RequiredArgsConstructor
@Tag(name = "OTP", description = "Issue and verify one-time passwords")
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/send")
    @Operation(summary = "Issue an OTP for a phone number")
    public ResponseEntity<SendOtpResponse> send(
            @AuthenticationPrincipal ApiKey key,
            @Valid @RequestBody SendOtpRequest request) {
        OtpService.Issued issued = otpService.send(key.getId(), request);
        OtpCode e = issued.entity();
        SendOtpResponse body = new SendOtpResponse(
                e.getId(),
                e.getPhone(),
                e.getCodeHash() == null ? null : issued.rawCode().length(),
                e.getExpiresAt(),
                issued.rawCode()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify an OTP code for a phone number")
    public VerifyOtpResponse verify(
            @AuthenticationPrincipal ApiKey key,
            @Valid @RequestBody VerifyOtpRequest request) {
        return otpService.verify(request.phone(), request.code());
    }
}
