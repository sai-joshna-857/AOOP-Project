package casestudy5;

import java.util.Optional;

public interface UserRepository {
    void saveNew(UserAccount account);
    Optional<UserAccount> findByEmail(EmailAddress email);
    boolean existsByEmail(EmailAddress email);
    void changeEmailKey(EmailAddress oldEmail, EmailAddress newEmail, UserAccount account);
}