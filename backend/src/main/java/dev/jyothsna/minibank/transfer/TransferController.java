package dev.jyothsna.minibank.transfer;

import java.util.List;

import dev.jyothsna.minibank.account.PayeeResponse;
import dev.jyothsna.minibank.common.ApiException;
import dev.jyothsna.minibank.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
class TransferController {

	private static final int RECENT_PAYEES = 5;

	private final TransferService transferService;

	private final TransferRepository transfers;

	TransferController(TransferService transferService, TransferRepository transfers) {
		this.transferService = transferService;
		this.transfers = transfers;
	}

	@GetMapping("/recent-payees")
	List<PayeeResponse> recentPayees(@AuthenticationPrincipal Jwt jwt) {
		return transfers.findRecentPayees(CurrentUser.id(jwt), PageRequest.of(0, RECENT_PAYEES))
			.stream()
			.map(payee -> new PayeeResponse(payee.getAccountNumber(), PayeeResponse.mask(payee.getFullName())))
			.toList();
	}

	@PostMapping
	ResponseEntity<TransferResponse> create(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody TransferRequest request) {
		if (idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
			throw ApiException.badRequest("invalid_idempotency_key", "Idempotency-Key must be 1-64 characters");
		}
		TransferService.Outcome outcome = transferService.transfer(CurrentUser.id(jwt), request, idempotencyKey);
		if (outcome.replayed()) {
			return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(outcome.transfer());
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(outcome.transfer());
	}

}
