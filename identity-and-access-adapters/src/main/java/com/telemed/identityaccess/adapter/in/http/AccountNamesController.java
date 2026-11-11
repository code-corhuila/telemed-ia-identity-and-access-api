package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DEC-013: names of several accounts. Under /api/v1/auth so the gateway already routes
 * it; AuthFilter answers 401 without a valid token and the use case decides the role.
 */
@RestController
@RequestMapping(AuthRoutes.ACCOUNT_NAMES_PATH)
public class AccountNamesController {

    private static final String IDS_ERROR =
            "ids must be 1 to " + LookupAccountNamesUseCase.MAX_IDS + " account UUIDs separated by commas.";

    private final LookupAccountNamesUseCase lookupAccountNames;

    public AccountNamesController(LookupAccountNamesUseCase lookupAccountNames) {
        this.lookupAccountNames = lookupAccountNames;
    }

    public record AccountNameResponse(UUID id, String fullName) {
    }

    public record AccountNamesResponse(List<AccountNameResponse> data) {
    }

    @GetMapping
    public AccountNamesResponse lookup(
            @RequestParam(required = false) String ids,
            HttpServletRequest request
    ) {
        Set<UUID> accountIds = parseIds(ids);
        AuthenticatedPrincipal caller = AuthenticatedRequestContext.require(request);

        return new AccountNamesResponse(
                lookupAccountNames.lookup(caller.role(), accountIds).stream()
                        .map(name -> new AccountNameResponse(name.id(), name.fullName()))
                        .toList()
        );
    }

    private static Set<UUID> parseIds(String ids) {
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
