package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.domain.model.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FR-012: account administration. Under /api/v1/auth so the gateway already routes it;
 * AuthFilter answers 401 without a valid token and 403 for any role but ADMIN.
 */
@RestController
@RequestMapping(AuthRoutes.ADMIN_ACCOUNTS_PATH)
public class AdminAccountsController {

    private final BrowseAccountsUseCase browseAccounts;

    public AdminAccountsController(BrowseAccountsUseCase browseAccounts) {
        this.browseAccounts = browseAccounts;
    }

    public record AccountResponse(
            UUID id,
            String fullName,
            String email,
            String identityDocument,
            String role,
            boolean active,
            Instant registrationDate,
            Instant lastAccess
    ) {

        static AccountResponse from(AccountView account) {
            return new AccountResponse(
                    account.id(),
                    account.fullName(),
                    account.email(),
                    account.identityDocument(),
                    account.role().name(),
                    account.active(),
                    account.registrationDate(),
                    account.lastAccess()
            );
        }
    }

    public record PageMeta(int page, int limit, long total, long totalPages) {
    }

    public record AccountListResponse(List<AccountResponse> data, PageMeta meta) {
    }

    @GetMapping
    public AccountListResponse list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String limit
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        Integer pageNumber = parseInt(page, 1, errors, "page", "page must be a number from 1.");
        Integer pageLimit = parseInt(limit, 20, errors, "limit", "limit must be between 1 and 100.");
        if (pageNumber != null && pageNumber < 1) {
            errors.put("page", "page must be a number from 1.");
        }
        if (pageLimit != null && (pageLimit < 1 || pageLimit > BrowseAccountsUseCase.MAX_LIMIT)) {
            errors.put("limit", "limit must be between 1 and 100.");
        }
        Role roleFilter = parseRole(role, errors);
        Boolean activeFilter = parseActive(active, errors);

        if (!errors.isEmpty()) {
            throw new InvalidQueryParametersException(errors);
        }

        var result = browseAccounts.browse(new BrowseAccountsUseCase.Query(
                q, roleFilter, activeFilter, pageNumber, pageLimit));

        return new AccountListResponse(
                result.items().stream().map(AccountResponse::from).toList(),
                new PageMeta(result.page(), result.limit(), result.total(), result.totalPages())
        );
    }

    @GetMapping("/{id}")
    public AccountResponse view(@PathVariable String id) {
        UUID accountId;
        try {
            accountId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            // A malformed id names no account.
            throw new AccountNotFoundException();
        }

        return AccountResponse.from(browseAccounts.view(accountId));
    }

    private static Integer parseInt(
            String value,
            int fallback,
            Map<String, String> errors,
            String field,
            String message
    ) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            errors.put(field, message);
            return null;
        }
    }

    private static Role parseRole(String value, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            errors.put("role", "role must be PATIENT, PROFESSIONAL or ADMIN.");
            return null;
        }
    }

    private static Boolean parseActive(String value, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim()) {
            case "true" -> true;
            case "false" -> false;
            default -> {
                errors.put("active", "active must be true or false.");
                yield null;
            }
        };
    }
}
