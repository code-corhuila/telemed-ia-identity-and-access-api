package com.telemed.identityaccess.application.port.in;

import java.util.UUID;

/**
 * The onboarding saga activates a professional account as its last step, or deactivates it to
 * compensate. Idempotent; any account that is not a professional is answered as not found.
 */
public interface ChangeProfessionalAccountStatusUseCase {

    void change(UUID accountId, boolean active);
}
