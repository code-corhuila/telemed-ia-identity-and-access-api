package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DEC-013: names of several accounts. Under /api/v1/auth so the gateway already routes
 * it; AuthFilter answers 401 without a valid token and the use case decides the role.
 */
@RestController
@RequestMapping(AuthRoutes.ACCOUNT_NAMES_PATH)
public class AccountNamesController {

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
        Set<UUID> accountIds = AccountIdsParameter.parse(ids);
        AuthenticatedPrincipal caller = AuthenticatedRequestContext.require(request);

        return new AccountNamesResponse(
                lookupAccountNames.lookup(caller.role(), accountIds).stream()
                        .map(name -> new AccountNameResponse(name.id(), name.fullName()))
                        .toList()
        );
    }
}
