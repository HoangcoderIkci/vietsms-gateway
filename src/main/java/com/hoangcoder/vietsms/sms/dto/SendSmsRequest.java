package com.hoangcoder.vietsms.sms.dto;

import com.hoangcoder.vietsms.validation.VietnamesePhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendSmsRequest(
        @VietnamesePhone
        String to,

        @NotBlank
        @Size(max = 160, message = "content must be at most 160 characters")
        String content,

        @Size(max = 64, message = "client_message_id must be at most 64 characters")
        String clientMessageId
) {}
