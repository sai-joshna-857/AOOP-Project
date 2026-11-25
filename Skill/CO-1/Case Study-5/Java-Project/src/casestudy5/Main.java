package casestudy5;

import java.time.Duration;
import java.time.Instant;

public class Main {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        RegistrationService service =
                new RegistrationService(new InMemoryUserRepository());
        Instant now = Instant.now();

        System.out.println("==============================================================");
        System.out.println(" CO-1 CASE STUDY 5 - User Accounts & Email Verification Demo");
        System.out.println("==============================================================");

        // Scenario 1
        section("1. Bug: users register with incorrectly formatted emails");
        expectReject("Email 'not-an-email' is rejected", () -> EmailAddress.of("not-an-email"));
        expectReject("Email 'asha@@example.com' is rejected", () -> EmailAddress.of("asha@@example.com"));
        expectReject("Email 'asha@example' (no TLD) is rejected", () -> EmailAddress.of("asha@example"));
        expectReject("Email 'a sha@example.com' (space) is rejected", () -> EmailAddress.of("a sha@example.com"));
        expectReject("Registration with a bad email is rejected",
                () -> service.register("bad-email", "password123"));

        // Scenario 2
        section("2. Registration: account starts UNVERIFIED and cannot log in");
        var reg = service.register("  Asha@Example.COM  ", "password123");
        UserAccount asha = reg.account();
        check("Email was normalised to lower-case",
                asha.email().value().equals("asha@example.com"));
        check("Status is PENDING_VERIFICATION", asha.status() == AccountStatus.PENDING_VERIFICATION);
        check("Verification state is PendingVerification",
                asha.verification() instanceof EmailVerification.PendingVerification);
        expectReject("Login before verification is refused",
                () -> service.login("asha@example.com", "password123"));

        // Scenario 3
        section("3. Bug: duplicate accounts using the same email, different capitalisation");
        expectReject("Registering 'ASHA@example.com' again is rejected",
                () -> service.register("ASHA@example.com", "password456"));
        expectReject("Registering 'asha@EXAMPLE.COM' again is rejected",
                () -> service.register("asha@EXAMPLE.COM", "password456"));

        // Scenario 4
        section("4. Bug: tokens accepted after expiry / wrong tokens accepted");
        expectReject("Wrong token secret is rejected",
                () -> asha.verifyEmail("wrong-secret-that-is-long-enough-123456", now));
        check("Account is still pending after a wrong token",
                asha.verification() instanceof EmailVerification.PendingVerification);

        EmailAddress expiredMail = EmailAddress.of("expired@example.com");
        VerificationToken expiredToken = VerificationToken.generate(
                expiredMail, now.minus(Duration.ofHours(25)), VerificationToken.DEFAULT_TTL);
        UserAccount expiredAccount = UserAccount.register(
                UserId.generate(), expiredMail, expiredToken, Passwords.hash("password123"));
        expectReject("Expired token (issued 25h ago, 24h TTL) is rejected",
                () -> expiredAccount.verifyEmail(expiredToken.secret(), now));

        // Scenario 5
        section("5. Correct token verifies - and the verified state CARRIES its date");
        asha.verifyEmail(reg.tokenSecret(), now);
        check("Account is now verified", asha.isVerified());
        if (asha.verification() instanceof EmailVerification.Verified v) {
            check("Verified state holds a verification date (cannot exist without one)",
                    v.verifiedAt() != null);
            System.out.println("       verifiedAt = " + v.verifiedAt());
        } else {
            check("Verified state holds a verification date", false);
        }
        check("Status became ACTIVE", asha.status() == AccountStatus.ACTIVE);
        expectReject("The same token cannot be used twice",
                () -> asha.verifyEmail(reg.tokenSecret(), now));
        check("Login now succeeds with the right password",
                service.login("asha@example.com", "password123") == asha);
        expectReject("Login with a wrong password is refused",
                () -> service.login("asha@example.com", "wrongpassword"));

        // Scenario 6
        section("6. Resend: a new token REPLACES the old one (old link dies)");
        var reg2 = service.register("bindu@example.com", "password123");
        UserAccount bindu = reg2.account();
        String newSecret = service.resendVerification(bindu);
        expectReject("Old token stops working after a resend",
                () -> bindu.verifyEmail(reg2.tokenSecret(), now));
        bindu.verifyEmail(newSecret, now);
        check("New token verifies the account", bindu.isVerified());

        // Scenario 7
        section("7. Bug: phone numbers stored in different formats");
        PhoneNumber p1 = PhoneNumber.parse("098765 43210");
        PhoneNumber p2 = PhoneNumber.parse("+91 98765-43210");
        PhoneNumber p3 = PhoneNumber.parse("919876543210");
        check("'098765 43210' normalises to +919876543210", p1.value().equals("+919876543210"));
        check("All three formats are the SAME phone number", p1.equals(p2) && p2.equals(p3));
        expectReject("Phone '123' is rejected", () -> PhoneNumber.parse("123"));
        asha.setPhone(p1);
        System.out.println("       Stored on account as: " + asha.phone().orElseThrow());

        // Scenario 8
        section("8. Bug: postal addresses with incomplete information");
        asha.setAddress(PostalAddress.of("42 MG Road", "Hyderabad", "500001", "India"));
        check("Complete address is accepted", asha.address().isPresent());
        expectReject("Address with a blank city is rejected",
                () -> PostalAddress.of("MG Road", "  ", "500001", "India"));
        expectReject("Address with a bad PIN code is rejected",
                () -> PostalAddress.of("MG Road", "Hyderabad", "1234", "India"));

        // Scenario 9
        section("9. Changing the email RESETS verification for the new address");
        String newMailSecret = service.changeEmail(asha, "Asha.New@Example.org");
        check("Email changed and normalised",
                asha.email().value().equals("asha.new@example.org"));
        check("Verification is pending again (old trust does not carry over)",
                asha.verification() instanceof EmailVerification.PendingVerification);
        expectReject("Login is refused until the new email is verified",
                () -> service.login("asha.new@example.org", "password123"));
        asha.verifyEmail(newMailSecret, Instant.now());
        check("Login works again after re-verification",
                service.login("asha.new@example.org", "password123") == asha);

        // Scenario 10
        section("10. Bug: suspended users are still able to log in");
        asha.suspend();
        check("Status is SUSPENDED", asha.status() == AccountStatus.SUSPENDED);
        expectReject("Suspended account cannot log in",
                () -> service.login("asha.new@example.org", "password123"));
        asha.reactivate();
        check("Reactivated account can log in again",
                service.login("asha.new@example.org", "password123") == asha);
        asha.delete();
        expectReject("Deleted account cannot log in",
                () -> service.login("asha.new@example.org", "password123"));
        expectReject("Deleted account cannot be reactivated", asha::reactivate);
        expectReject("Deleted account cannot change email",
                () -> service.changeEmail(asha, "zombie@example.com"));

        // Summary
        System.out.println();
        System.out.println("==============================================================");
        System.out.printf(" RESULT: %d passed, %d failed%n", passed, failed);
        System.out.println(failed == 0
                ? " ALL CHECKS PASSED - every reported bug is prevented by the design."
                : " SOME CHECKS FAILED - see above.");
        System.out.println("==============================================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("--- " + title);
    }

    private static void check(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + label);
        } else {
            failed++;
            System.out.println("  FAIL  " + label);
        }
    }

    private static void expectReject(String label, Runnable action) {
        try {
            action.run();
            failed++;
            System.out.println("  FAIL  " + label + "  (nothing was thrown!)");
        } catch (DomainException e) {
            passed++;
            System.out.println("  PASS  " + label + "  ->  " + e.getMessage());
        }
    }
}