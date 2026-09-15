package dev.jyothsna.minibank.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.jyothsna.minibank.common.CurrentUser;
import dev.jyothsna.minibank.common.PageResponse;
import dev.jyothsna.minibank.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
class AccountController {

	private final AccountService accountService;

	private final UserRepository users;

	AccountController(AccountService accountService, UserRepository users) {
		this.accountService = accountService;
		this.users = users;
	}

	@GetMapping
	List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return accountService.listForOwner(CurrentUser.id(jwt));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AccountResponse open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenAccountRequest request) {
		var owner = users.getReferenceById(CurrentUser.id(jwt));
		return AccountResponse.from(accountService.openAccount(owner, request.type(), request.nickname(),
				BigDecimal.ZERO, null, Instant.now()));
	}

	@GetMapping("/lookup")
	PayeeResponse lookup(@RequestParam String number) {
		return accountService.lookupPayee(number);
	}

	@GetMapping("/{id}")
	AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return accountService.getForOwner(CurrentUser.id(jwt), id);
	}

	@GetMapping("/{id}/transactions")
	PageResponse<LedgerEntryResponse> transactions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@RequestParam(required = false) String direction, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return accountService.history(CurrentUser.id(jwt), id, new AccountService.HistoryFilter(direction, from, to, q),
				page, size);
	}

}
