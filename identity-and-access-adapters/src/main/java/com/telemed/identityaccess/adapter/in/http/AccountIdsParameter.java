package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The ids query parameter of the account names endpoints: 1 to 50 UUIDs separated by commas. */
final class AccountIdsParameter {

    private static final String IDS_ERROR =
            "ids must be 1 to " + LookupAccountNamesUseCase.MAX_IDS + " account UUIDs separated by commas.";

    private AccountIdsParameter() {
    }

    static Set<UUID> parse(String ids) {
        Set<UUID> accountIds = new LinkedHashSet<>();
        if (ids != null) {
            for (String part : ids.split(",")) {
                String id = part.strip();
                if (id.isEmpty()) {
                    continue;
                }
                try {
                    accountIds.add(UUID.fromString(id));
                } catch (IllegalArgumentException malformed) {
                    throw invalidIds();
                }
            }
        }
        if (accountIds.isEmpty() || accountIds.size() > LookupAccountNamesUseCase.MAX_IDS) {
            throw invalidIds();
        }
        return accountIds;
    }

    private static InvalidQueryParametersException invalidIds() {
        return new InvalidQueryParametersException(Map.of("ids", IDS_ERROR));
    }
}
