package com.telemed.identityaccess.adapter.in.http;

public final class AuthRoutes {

    public static final String BASE =
            "/api/v1/auth";

    public static final String SESSION =
            "/session";

    public static final String REFRESH =
            "/refresh";

    public static final String SESSION_PATH =
            BASE + SESSION;

    public static final String REFRESH_PATH =
            BASE + REFRESH;

    private AuthRoutes() {
    }
}