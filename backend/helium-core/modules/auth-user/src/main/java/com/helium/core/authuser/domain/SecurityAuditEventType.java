package com.helium.core.authuser.domain;

public enum SecurityAuditEventType {
    AUTH_SIGNUP("AUTH.SIGNUP"),
    AUTH_EMAIL_VERIFIED("AUTH.EMAIL_VERIFIED"),
    AUTH_LOGIN_SUCCESS("AUTH.LOGIN_SUCCESS"),
    AUTH_LOGIN_FAILED("AUTH.LOGIN_FAILED"),
    USER_REGISTERED,
    EMAIL_VERIFICATION_ISSUED,
    EMAIL_VERIFIED,
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_REACTIVATED,
    LOGOUT,
    SESSION_REVOKED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_CHANGED,
    ROLE_GRANTED,
    ROLE_REVOKED;

    private final String code;

    SecurityAuditEventType() {
        this.code = name();
    }

    SecurityAuditEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
