package casestudy5;

import java.util.Objects;

public final class PhoneNumber {

    public static final String DEFAULT_COUNTRY_CODE = "91"; // India

    private final String e164;

    private PhoneNumber(String e164) {
        this.e164 = e164;
    }

    public static PhoneNumber parse(String raw) {
        return parse(raw, DEFAULT_COUNTRY_CODE);
    }

    public static PhoneNumber parse(String raw, String defaultCountryCode) {
        if (raw == null) {
            throw new DomainException("Phone number must not be null");
        }
        String s = raw.replaceAll("[\\s\\-().]", "");
        if (s.isEmpty()) {
            throw new DomainException("Phone number must not be empty");
        }

        if (s.startsWith("00")) {
            s = "+" + s.substring(2);
        }
        if (!s.startsWith("+")) {
            String digits = s;
            if (!digits.chars().allMatch(Character::isDigit)) {
                throw new DomainException("Invalid characters in phone number: '" + raw + "'");
            }
            if (digits.startsWith("0") && digits.length() == 11) {
                digits = defaultCountryCode + digits.substring(1);
            } else if (digits.length() == 10) {
                digits = defaultCountryCode + digits;
            } else if (!digits.startsWith(defaultCountryCode)) {
                throw new DomainException("Cannot tell the country code of phone number: '" + raw + "'");
            }
            s = "+" + digits;
        }

        if (!s.substring(1).chars().allMatch(Character::isDigit)
                || !s.matches("\\+[1-9]\\d{7,14}")) {
            throw new DomainException("Invalid phone number: '" + raw + "'");
        }
        if (s.startsWith("+91")) {
            String national = s.substring(3);
            if (national.length() != 10 || !national.matches("[6-9]\\d{9}")) {
                throw new DomainException("Invalid Indian phone number: '" + raw + "'");
            }
        }
        return new PhoneNumber(s);
    }

    public String value() {
        return e164;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PhoneNumber other && e164.equals(other.e164);
    }

    @Override
    public int hashCode() {
        return Objects.hash(e164);
    }

    @Override
    public String toString() {
        return e164;
    }
}