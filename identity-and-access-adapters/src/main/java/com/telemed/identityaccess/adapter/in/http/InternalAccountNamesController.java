package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.adapter.in.http.AccountNamesController.AccountNameResponse;
import com.telemed.identityaccess.adapter.in.http.AccountNamesController.AccountNamesResponse;
import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEC-014: names of several accounts for service clients. The gateway does not route /internal;
 * ServiceCallerFilter lets only the services in security.account-names.clients through.
 */
@RestController
public class InternalAccountNamesController {

    public static final String PATH = "/internal/account-names";

    private final LookupAccountNamesUseCase lookupAccountNames;

    public InternalAccountNamesController(LookupAccountNamesUseCase lookupAccountNames) {
        this.lookupAccountNames = lookupAccountNames;
    }

    @GetMapping(PATH)
    public AccountNamesResponse lookup(@RequestParam(required = false) String ids) {
        return new AccountNamesResponse(
                lookupAccountNames.lookupForService(AccountIdsParameter.parse(ids)).stream()
                        .map(name -> new AccountNameResponse(name.id(), name.fullName()))
                        .toList()
        );
    }
}
