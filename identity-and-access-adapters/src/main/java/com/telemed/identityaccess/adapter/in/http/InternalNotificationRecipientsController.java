package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.FindNotificationRecipientUseCase;
import com.telemed.identityaccess.application.port.in.FindNotificationRecipientUseCase.NotificationRecipient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Service-to-service only: the gateway does not route /internal. ServiceCallerFilter lets
 * only the services in security.notification-recipients.clients through.
 */
@RestController
public class InternalNotificationRecipientsController {

    public static final String PATH = "/internal/notification-recipients";

    private final FindNotificationRecipientUseCase findRecipient;

    public InternalNotificationRecipientsController(FindNotificationRecipientUseCase findRecipient) {
        this.findRecipient = findRecipient;
    }

    /** The e-mail of an active patient; 404 for any other account. */
    @GetMapping(PATH + "/{id}")
    public NotificationRecipient find(@PathVariable String id) {
        UUID accountId;
        try {
            accountId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new InvalidQueryParametersException(Map.of("id", "id must be an account id (UUID)."));
        }
        return findRecipient.find(accountId);
    }
}
