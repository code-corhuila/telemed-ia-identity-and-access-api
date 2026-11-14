package com.telemed.identityaccess.application.port.in;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Professional management lists professionals by their account id and shows their
 * name, which identity owns. Only PROFESSIONAL accounts are answered, with these fields.
 */
public interface LookupProfessionalAccountsUseCase {

    int MAX_IDS = 100;

    /** Unknown ids and accounts of any other role are left out. */
    List<ProfessionalAccount> lookup(Set<UUID> accountIds);

    record ProfessionalAccount(
            UUID id,
            String fullName,
            String email
    ) {
    }
}
