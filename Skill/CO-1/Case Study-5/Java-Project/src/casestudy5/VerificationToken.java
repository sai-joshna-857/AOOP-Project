package casestudy5;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

public record VerificationToken(String secret, EmailAddress issuedFor, Instant expiresAt) {

    private static final SecureRandom RANDOM = new SecureRandom();
    public static final Duration DEFAULT_TTL = Duration.ofHours(24);

    public VerificationToken {
        if (secret == null || secret.length() < 32) {
            throw new DomainException("Verification token secret is too weak");
        }
        Objects.requireNonNull(issuedFor, "issuedFor");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static VerificationToken generate(EmailAddress issuedFor, Instant now, Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new VerificationToken(secret, issuedFor, now.plus(ttl));
    }

    public boolean isValidFor(EmailAddress email, String presentedSecret, Instant now) {
        if (presentedSecret == null) {
            return false;
        }
        boolean sameSecret = MessageDigest.isEqual(
            secret.getBytes(StandardCharsets.UTF_8),
            presentedSecret.getBytes(StandardCharsets.UTF_8));
        return issuedFor.equals(email) && sameSecret && now.isBefore(expiresAt);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}