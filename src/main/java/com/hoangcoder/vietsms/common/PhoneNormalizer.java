package com.hoangcoder.vietsms.common;

import java.util.Set;
import java.util.regex.Pattern;

public final class PhoneNormalizer {

    private static final Pattern E164_VN = Pattern.compile("^\\+84(3|5|7|8|9)\\d{8}$");
    private static final Set<String> VALID_PREFIXES = Set.of("3", "5", "7", "8", "9");

    private PhoneNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("[\\s\\-()]+", "");
        if (s.startsWith("+84")) {
            return s;
        }
        if (s.startsWith("84") && s.length() == 11) {
            return "+" + s;
        }
        if (s.startsWith("0") && s.length() == 10) {
            return "+84" + s.substring(1);
        }
        return s;
    }

    public static boolean isValid(String normalized) {
        return normalized != null && E164_VN.matcher(normalized).matches();
    }

    public static String mask(String phone) {
        if (phone == null || phone.length() < 6) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }

    public static Set<String> validPrefixes() {
        return VALID_PREFIXES;
    }
}
