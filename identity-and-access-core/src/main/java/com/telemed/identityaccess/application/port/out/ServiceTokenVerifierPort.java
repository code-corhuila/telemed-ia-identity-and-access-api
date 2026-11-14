package com.telemed.identityaccess.application.port.out;

/** Reads who calls an internal endpoint; throws AccessTokenVerificationException for a bad token. */
public interface ServiceTokenVerifierPort {

    ServiceCaller verify(String token);

    /** subject is the client id for role SERVICE, and the user id for any other role. */
    record ServiceCaller(
            String subject,
            String role
    ) {

        public boolean isService(String clientId) {
            return "SERVICE".equals(role) && clientId.equals(subject);
        }
    }
}
