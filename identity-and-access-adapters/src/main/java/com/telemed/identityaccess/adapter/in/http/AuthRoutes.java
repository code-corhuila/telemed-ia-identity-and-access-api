package com.telemed.identityaccess.adapter.in.http;

public final class AuthRoutes {

    public static final String BASE =
            "/api/v1/auth";

    public static final String SESSION =
            "/session";

    public static final String REFRESH =
            "/refresh";

    public static final String LOGOUT =
            "/logout";

    public static final String PASSWORD_RECOVERY =
            "/password-recovery";

    public static final String SESSION_PATH =
            BASE + SESSION;

    public static final String REFRESH_PATH =
            BASE + REFRESH;

    public static final String LOGOUT_PATH =
            BASE + LOGOUT;

    public static final String PASSWORD_RECOVERY_PATH =
            BASE + PASSWORD_RECOVERY;

    private AuthRoutes() {
    }
}