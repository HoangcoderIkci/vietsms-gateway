package com.hoangcoder.vietsms.validation;

import com.hoangcoder.vietsms.common.PhoneNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class VietnamesePhoneValidator implements ConstraintValidator<VietnamesePhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return false;
        return PhoneNormalizer.isValid(PhoneNormalizer.normalize(value));
    }
}
