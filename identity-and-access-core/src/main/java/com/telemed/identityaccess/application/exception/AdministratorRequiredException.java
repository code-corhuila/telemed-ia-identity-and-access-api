package com.telemed.identityaccess.application.exception;

/** The requester's token says ADMIN, but their account is no longer an active administrator. */
public final class AdministratorRequiredException extends RuntimeException {

    public AdministratorRequiredException() {
        super("Administrator role is required.");
    }
}
