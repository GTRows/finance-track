package com.fintrack.account;

import com.fintrack.account.dto.AccountResponse;
import com.fintrack.account.dto.AccountTotalsResponse;
import com.fintrack.account.dto.CreateAccountRequest;
import com.fintrack.account.dto.UpdateAccountRequest;
import com.fintrack.auth.FinTrackUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for managing user accounts. */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /** Lists all live accounts owned by the authenticated user. */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(accountService.listForUser(user.getId()));
    }

    /** Returns aggregate counts and per-currency balance rollup for the stat strip. */
    @GetMapping("/totals")
    public ResponseEntity<AccountTotalsResponse> totals(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(accountService.totalsForUser(user.getId()));
    }

    /** Returns a single account by id. */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> get(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getForUser(user.getId(), id));
    }

    /** Creates a new account. */
    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.create(user.getId(), request));
    }

    /** Updates an existing account's mutable fields. */
    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountRequest request) {
        return ResponseEntity.ok(accountService.update(user.getId(), id, request));
    }

    /** Soft-archives an account. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID id) {
        accountService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
