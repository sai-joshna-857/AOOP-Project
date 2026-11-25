package casestudy5;

import java.time.Instant;
import java.util.Objects;

public sealed interface EmailVerification {

    record Unverified() implements EmailVerification {
    }

    record PendingVerification(VerificationToken token) implements EmailVerification {
        public PendingVerification {
            Objects.requireNonNull(token, "A pending verification must have a token");
        }
    }

    record Verified(Instant verifiedAt) implements EmailVerification {
        public Verified {
            Objects.requireNonNull(verifiedAt, "A verified email must have a verification date");
        }
    }
}