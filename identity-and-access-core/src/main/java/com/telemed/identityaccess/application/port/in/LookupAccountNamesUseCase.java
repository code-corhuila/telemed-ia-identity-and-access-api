package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.domain.model.Role;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DEC-013: appointments and consultations carry only account ids; a professional needs
 * the names of its patients and an administrator the name of anyone.
 */
public interface LookupAccountNamesUseCase {

    int MAX_IDS = 50;

    /**
     * A PROFESSIONAL gets only names of PATIENT accounts, an ADMIN any account. Ids the
     * caller may not see, or that do not exist, are left out.
     *
     * @throws com.telemed.identityaccess.application.exception.AccountNamesNotAllowedException
     *         for any other role
     */
    List<AccountName> lookup(Role callerRole, Set<UUID> accountIds);

    /**
     * DEC-014: names of accounts of any role for an allowed service client. The caller is
     * checked before this is reached (ServiceCallerFilter). Unknown ids are left out.
     */
    List<AccountName> lookupForService(Set<UUID> accountIds);

    record AccountName(
            UUID id,
            String fullName
    ) {
    }
}
