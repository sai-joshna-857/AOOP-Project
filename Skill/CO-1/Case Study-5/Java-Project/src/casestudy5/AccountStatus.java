package casestudy5;

public enum AccountStatus {
    PENDING_VERIFICATION, // registered, email not yet verified - cannot log in
    ACTIVE,               // verified - the ONLY status that can log in
    SUSPENDED,            // blocked by admin/user - cannot log in
    DELETED               // terminal - can never log in or be reactivated
}