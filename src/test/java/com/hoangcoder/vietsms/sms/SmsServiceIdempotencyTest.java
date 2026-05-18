package com.hoangcoder.vietsms.sms;

import com.hoangcoder.vietsms.security.ApiKeyService;
import com.hoangcoder.vietsms.sms.dto.SendSmsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SmsServiceIdempotencyTest {

    @Autowired
    SmsService smsService;

    @Autowired
    ApiKeyService apiKeyService;

    @Test
    void same_client_message_id_returns_existing_record() {
        var key = apiKeyService.issue("idem-test", "t@example.com", 100).entity();

        var req = new SendSmsRequest("0987654321", "hello", "order-123");
        SmsMessage first = smsService.send(key.getId(), req);
        SmsMessage second = smsService.send(key.getId(), req);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(first.getStatus()).isEqualTo(SmsStatus.QUEUED);
    }

    @Test
    void different_client_message_id_creates_new_record() {
        var key = apiKeyService.issue("idem-test-2", "t@example.com", 100).entity();

        SmsMessage a = smsService.send(key.getId(),
                new SendSmsRequest("0987654321", "msg a", "id-a"));
        SmsMessage b = smsService.send(key.getId(),
                new SendSmsRequest("0987654321", "msg b", "id-b"));

        assertThat(b.getId()).isNotEqualTo(a.getId());
    }

    @Test
    void null_client_message_id_always_creates_new_record() {
        var key = apiKeyService.issue("idem-test-3", "t@example.com", 100).entity();

        SmsMessage a = smsService.send(key.getId(),
                new SendSmsRequest("0987654321", "msg", null));
        SmsMessage b = smsService.send(key.getId(),
                new SendSmsRequest("0987654321", "msg", null));

        assertThat(b.getId()).isNotEqualTo(a.getId());
    }

    @Test
    void normalizes_phone_to_plus84_form() {
        var key = apiKeyService.issue("norm-test", "t@example.com", 100).entity();

        SmsMessage m = smsService.send(key.getId(),
                new SendSmsRequest("0987654321", "hi", null));

        assertThat(m.getToPhone()).isEqualTo("+84987654321");
    }
}
