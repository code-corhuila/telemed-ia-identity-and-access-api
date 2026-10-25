package com.telemed.identityaccess.application.port.in;

import java.util.UUID;

/**
 * Notifications e-mails a patient about their appointments and needs the address,
 * which identity owns. Only active PATIENT accounts are answered, with these fields.
 */
public interface FindNotificationRecipientUseCase {

    /** @throws com.telemed.identityaccess.application.exception.AccountNotFoundException otherwise */
    NotificationRecipient find(UUID accountId);

    record NotificationRecipient(
            UUID id,
            String email
    ) {
    }
}
