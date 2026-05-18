package com.hoangcoder.vietsms.otp;

import com.hoangcoder.vietsms.common.exceptions.TooEarlyException;
import com.hoangcoder.vietsms.otp.dto.SendOtpRequest;
import com.hoangcoder.vietsms.otp.dto.VerifyOtpResponse;
import com.hoangcoder.vietsms.security.ApiKey;
import com.hoangcoder.vietsms.security.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "vietsms.ratelimit.otp-per-phone-cooldown-seconds=0",
        "vietsms.otp.default-ttl-seconds=300",
        "vietsms.otp.max-attempts=3"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OtpServiceTest {

    @Autowired OtpService otpService;
    @Autowired OtpRepository otpRepository;
    @Autowired ApiKeyService apiKeyService;

    ApiKey key;

    @BeforeEach
    void setUp() {
        key = apiKeyService.issue("otp-test-" + System.nanoTime(), "t@example.com", 100).entity();
    }

    @Test
    void send_issues_six_digit_code_by_default() {
        var issued = otpService.send(key.getId(),
                new SendOtpRequest("0987654321", null, null));

        assertThat(issued.rawCode()).hasSize(6).matches("\\d{6}");
        assertThat(issued.entity().getPhone()).isEqualTo("+84987654321");
        assertThat(issued.entity().getAttempts()).isZero();
        assertThat(issued.entity().getLocked()).isFalse();
    }

    @Test
    void verify_returns_ok_on_correct_code() {
        var issued = otpService.send(key.getId(),
                new SendOtpRequest("0987654321", null, null));

        VerifyOtpResponse result = otpService.verify("0987654321", issued.rawCode());

        assertThat(result.verified()).isTrue();
    }

    @Test
    void verify_locks_after_max_attempts() {
        otpService.send(key.getId(), new SendOtpRequest("0911111111", null, null));

        VerifyOtpResponse r1 = otpService.verify("0911111111", "000000");
        VerifyOtpResponse r2 = otpService.verify("0911111111", "000000");
        VerifyOtpResponse r3 = otpService.verify("0911111111", "000000");

        assertThat(r1.verified()).isFalse();
        assertThat(r1.attemptsLeft()).isEqualTo(2);
        assertThat(r2.attemptsLeft()).isEqualTo(1);
        assertThat(r3.verified()).isFalse();
        assertThat(r3.reason()).isEqualTo("LOCKED");
    }

    @Test
    void verify_returns_no_otp_for_never_sent_phone() {
        VerifyOtpResponse r = otpService.verify("0922222222", "123456");
        assertThat(r.verified()).isFalse();
        assertThat(r.reason()).isEqualTo("NO_OTP_ISSUED");
    }

    @Test
    void verify_returns_expired_for_stale_code() {
        var issued = otpService.send(key.getId(),
                new SendOtpRequest("0933333333", null, null));
        // force-expire the code
        OtpCode e = otpRepository.findById(issued.entity().getId()).orElseThrow();
        e.setExpiresAt(Instant.now().minusSeconds(10));
        otpRepository.save(e);

        VerifyOtpResponse r = otpService.verify("0933333333", issued.rawCode());

        assertThat(r.verified()).isFalse();
        assertThat(r.reason()).isEqualTo("EXPIRED");
    }
}
