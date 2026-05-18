package com.hoangcoder.vietsms.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "0987654321,+84987654321",
            "+84987654321,+84987654321",
            "84987654321,+84987654321",
            "0987 654 321,+84987654321",
            "0987-654-321,+84987654321",
            "(098) 7654321,+84987654321"
    })
    void normalize_handles_common_vn_formats(String input, String expected) {
        assertThat(PhoneNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    void normalize_returns_null_for_null() {
        assertThat(PhoneNormalizer.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+84987654321",
            "+84355555555",
            "+84777777777",
            "+84888888888",
            "+84599999999"
    })
    void isValid_accepts_valid_prefixes(String normalized) {
        assertThat(PhoneNormalizer.isValid(normalized)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+84187654321",   // prefix 1 invalid
            "+84287654321",   // prefix 2 invalid
            "+8498765432",    // too short
            "+849876543210",  // too long
            "0987654321",     // not normalized
            ""
    })
    void isValid_rejects_invalid(String s) {
        assertThat(PhoneNormalizer.isValid(s)).isFalse();
    }

    @Test
    void mask_keeps_first3_last3() {
        assertThat(PhoneNormalizer.mask("+84987654321")).isEqualTo("+84****321");
    }
}
