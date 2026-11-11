package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** DEC-013: names of several accounts for professionals and administrators. */
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
    public AccountNamesResponse lookup(@RequestParam(required = false) String ids) {
        return new AccountNamesResponse(List.of());
    }
}
