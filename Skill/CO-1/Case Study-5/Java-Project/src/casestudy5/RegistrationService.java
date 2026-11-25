package casestudy5;

import java.time.Instant;

public final class RegistrationService {

    private final UserRepository repository;

    public RegistrationService(UserRepository repository) {
        this.repository = repository;
    }

    public record RegistrationResult(UserAccount account, String tokenSecret) {
    }

    public RegistrationResult register(String rawEmail, String password) {
        EmailAddress email = EmailAddress.of(rawEmail);
        if (repository.existsByEmail(email)) {
            throw new DomainException("Email already registered: " + email);
        }
        VerificationToken token = VerificationToken.generate(
                email, Instant.now(), VerificationToken.DEFAULT_TTL);
        UserAccount account = UserAccount.register(
                UserId.generate(), email, token, Passwords.hash(password));
        repository.saveNew(account);
        return new RegistrationResult(account, token.secret());
    }

    public String resendVerification(UserAccount account) {
        VerificationToken token = VerificationToken.generate(
                account.email(), Instant.now(), VerificationToken.DEFAULT_TTL);
        account.reissueToken(token);
        return token.secret();
    }

    public String changeEmail(UserAccount account, String rawNewEmail) {
        EmailAddress newEmail = EmailAddress.of(rawNewEmail);
        VerificationToken token = VerificationToken.generate(
                newEmail, Instant.now(), VerificationToken.DEFAULT_TTL);
        repository.changeEmailKey(account.email(), newEmail, account);
        account.changeEmail(newEmail, token);
        return token.secret();
    }

    public UserAccount login(String rawEmail, String password) {
        EmailAddress email = EmailAddress.of(rawEmail);
        UserAccount account = repository.findByEmail(email)
                .orElseThrow(() -> new DomainException("Login failed: account not available"));
        if (!account.passwordMatches(password)) {
            throw new DomainException("Login failed: incorrect password");
        }
        if (!account.canLogin()) {
            throw new DomainException(
                    "Login failed: account is " + account.status() + " and cannot log in");
        }
        return account;
    }
}