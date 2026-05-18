package com.hoangcoder.vietsms.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = VietnamesePhoneValidator.class)
public @interface VietnamesePhone {
    String message() default "must be a valid Vietnamese phone number (e.g. 0987654321 or +84987654321)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
