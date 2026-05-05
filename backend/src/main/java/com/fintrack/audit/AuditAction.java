package com.fintrack.audit;

public final class AuditAction {

    private AuditAction() {}

    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String TOTP_CHALLENGE_ISSUED = "TOTP_CHALLENGE_ISSUED";
    public static final String TOTP_VERIFY = "TOTP_VERIFY";
    public static final String TOTP_SETUP = "TOTP_SETUP";
    public static final String TOTP_ENABLE = "TOTP_ENABLE";
    public static final String TOTP_DISABLE = "TOTP_DISABLE";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String SESSION_REVOKE = "SESSION_REVOKE";
    public static final String SESSION_REVOKE_OTHERS = "SESSION_REVOKE_OTHERS";
    public static final String EMAIL_VERIFICATION_SENT = "EMAIL_VERIFICATION_SENT";
    public static final String EMAIL_VERIFICATION_CONFIRMED = "EMAIL_VERIFICATION_CONFIRMED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_CONFIRMED = "PASSWORD_RESET_CONFIRMED";
    public static final String TOTP_RECOVERY_REGENERATED = "TOTP_RECOVERY_REGENERATED";
    public static final String TOTP_RECOVERY_REDEEMED = "TOTP_RECOVERY_REDEEMED";
    public static final String PASSWORD_REHASHED = "PASSWORD_REHASHED";
    public static final String WEBAUTHN_REGISTER_STARTED = "WEBAUTHN_REGISTER_STARTED";
    public static final String WEBAUTHN_REGISTER_COMPLETED = "WEBAUTHN_REGISTER_COMPLETED";
    public static final String WEBAUTHN_REGISTER_FAILED = "WEBAUTHN_REGISTER_FAILED";
    public static final String WEBAUTHN_LOGIN = "WEBAUTHN_LOGIN";
    public static final String WEBAUTHN_LOGIN_FAILED = "WEBAUTHN_LOGIN_FAILED";
    public static final String WEBAUTHN_CLONE_DETECTED = "WEBAUTHN_CLONE_DETECTED";
    public static final String WEBAUTHN_AUTHENTICATOR_REVOKED = "WEBAUTHN_AUTHENTICATOR_REVOKED";
    public static final String REFRESH_FINGERPRINT_BOUND = "REFRESH_FINGERPRINT_BOUND";
    public static final String REFRESH_FINGERPRINT_MISMATCH = "REFRESH_FINGERPRINT_MISMATCH";
    public static final String AUDIT_RETENTION_PRUNED = "AUDIT_RETENTION_PRUNED";

    public static final String PORTFOLIO_CREATED = "PORTFOLIO_CREATED";
    public static final String PORTFOLIO_UPDATED = "PORTFOLIO_UPDATED";
    public static final String PORTFOLIO_DELETED = "PORTFOLIO_DELETED";

    public static final String HOLDING_CREATED = "HOLDING_CREATED";
    public static final String HOLDING_UPDATED = "HOLDING_UPDATED";
    public static final String HOLDING_DELETED = "HOLDING_DELETED";

    public static final String INVESTMENT_TRANSACTION_CREATED = "INVESTMENT_TRANSACTION_CREATED";
    public static final String INVESTMENT_TRANSACTION_UPDATED = "INVESTMENT_TRANSACTION_UPDATED";
    public static final String INVESTMENT_TRANSACTION_DELETED = "INVESTMENT_TRANSACTION_DELETED";

    public static final String BUDGET_TRANSACTION_CREATED = "BUDGET_TRANSACTION_CREATED";
    public static final String BUDGET_TRANSACTION_UPDATED = "BUDGET_TRANSACTION_UPDATED";
    public static final String BUDGET_TRANSACTION_DELETED = "BUDGET_TRANSACTION_DELETED";

    public static final String CATEGORY_CREATED = "CATEGORY_CREATED";
    public static final String CATEGORY_UPDATED = "CATEGORY_UPDATED";
    public static final String CATEGORY_DELETED = "CATEGORY_DELETED";

    public static final String BUDGET_RULE_CREATED = "BUDGET_RULE_CREATED";
    public static final String BUDGET_RULE_UPDATED = "BUDGET_RULE_UPDATED";
    public static final String BUDGET_RULE_DELETED = "BUDGET_RULE_DELETED";

    public static final String BILL_CREATED = "BILL_CREATED";
    public static final String BILL_UPDATED = "BILL_UPDATED";
    public static final String BILL_DELETED = "BILL_DELETED";

    public static final String BILL_PAYMENT_RECORDED = "BILL_PAYMENT_RECORDED";
}
