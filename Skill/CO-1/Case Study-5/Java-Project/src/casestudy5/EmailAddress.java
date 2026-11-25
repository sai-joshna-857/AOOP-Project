package casestudy5;

import java.util.Locale;
import java.util.Objects;

public final class EmailAddress {

    private final String normalised;

    private EmailAddress(String normalised) {
        this.normalised = normalised;
    }

    public static EmailAddress of(String raw) {
        if (raw == null) {
            throw new DomainException("Email address must not be null");
        }
        String email = raw.trim().toLowerCase(Locale.ROOT);

        if (email.isEmpty() || email.length() > 254) {
            throw new DomainException("Invalid email length: '" + raw + "'");
        }
        if (email.contains(" ")) {
            throw new DomainException("Email address must not contain spaces: '" + raw + "'");
        }
        int at = email.indexOf('@');
        if (at < 0 || at != email.lastIndexOf('@')) {
            throw new DomainException("Email address must contain exactly one '@': '" + raw + "'");
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);

        if (local.isEmpty() || local.length() > 64
                || !local.matches("[a-z0-9._%+\\-]+")
                || local.startsWith(".") || local.endsWith(".") || local.contains("..")) {
            throw new DomainException("Invalid local part of email address: '" + raw + "'");
        }
        if (!domain.contains(".")) {
            throw new DomainException("Email domain must contain a dot: '" + raw + "'");
        }
        String[] labels = domain.split("\\.");
        for (String label : labels) {
            if (!label.matches("[a-z0-9]([a-z0-9\\-]*[a-z0-9])?")) {
                throw new DomainException("Invalid domain in email address: '" + raw + "'");
            }
        }
        String tld = labels[labels.length - 1];
        if (tld.length() < 2 || !tld.chars().allMatch(Character::isLetter)) {
            throw new DomainException("Invalid top-level domain in email address: '" + raw + "'");
        }
        return new EmailAddress(email);
    }

    public String value() {
        return normalised;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EmailAddress other && normalised.equals(other.normalised);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalised);
    }

    @Override
    public String toString() {
        return normalised;
    }
}