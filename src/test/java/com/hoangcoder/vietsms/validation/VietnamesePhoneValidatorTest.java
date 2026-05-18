package com.hoangcoder.vietsms.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class VietnamesePhoneValidatorTest {

    private final VietnamesePhoneValidator validator = new VietnamesePhoneValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "0987654321",
            "+84987654321",
            "0 987 654 321",
            "0355555555"
    })
    void accepts_valid_vn_numbers(String phone) {
        assertThat(validator.isValid(phone, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0123456789",      // prefix 1 not allowed
            "987654321",       // missing leading 0
            "+84",
            "abc",
            "+84987"           // too short
    })
    void rejects_invalid(String phone) {
        assertThat(validator.isValid(phone, null)).isFalse();
    }

    @Test
    void rejects_null_and_blank() {
        assertThat(validator.isValid(null, null)).isFalse();
        assertThat(validator.isValid("", null)).isFalse();
        assertThat(validator.isValid("   ", null)).isFalse();
    }
}
