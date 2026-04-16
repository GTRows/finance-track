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
}
