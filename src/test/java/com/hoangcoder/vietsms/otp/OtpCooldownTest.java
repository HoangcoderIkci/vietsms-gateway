package com.hoangcoder.vietsms.otp;

import com.hoangcoder.vietsms.common.exceptions.TooEarlyException;
import com.hoangcoder.vietsms.otp.dto.SendOtpRequest;
import com.hoangcoder.vietsms.security.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "vietsms.ratelimit.otp-per-phone-cooldown-seconds=60"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OtpCooldownTest {

    @Autowired OtpService otpService;
    @Autowired ApiKeyService apiKeyService;

    @Test
    void second_send_within_cooldown_throws() {
        var key = apiKeyService.issue("cd-test", "t@example.com", 100).entity();
        otpService.send(key.getId(), new SendOtpRequest("0944444444", null, null));

        assertThatThrownBy(() -> otpService.send(key.getId(),
                new SendOtpRequest("0944444444", null, null)))
                .isInstanceOf(TooEarlyException.class);
    }
}
