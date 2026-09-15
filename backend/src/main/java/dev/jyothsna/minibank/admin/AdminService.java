package dev.jyothsna.minibank.admin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.jyothsna.minibank.account.Account;
import dev.jyothsna.minibank.account.AccountRepository;
import dev.jyothsna.minibank.account.AccountResponse;
import dev.jyothsna.minibank.account.AccountStatus;
import dev.jyothsna.minibank.common.ApiException;
import dev.jyothsna.minibank.transfer.TransferRepository;
import dev.jyothsna.minibank.user.Role;
import dev.jyothsna.minibank.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminService {

	private final UserRepository users;

	private final AccountRepository accounts;

	private final TransferRepository transfers;

	AdminService(UserRepository users, AccountRepository accounts, TransferRepository transfers) {
		this.users = users;
		this.accounts = accounts;
		this.transfers = transfers;
	}

	record Stats(long customers, long accounts, BigDecimal totalDeposits, long transfersLast24Hours) {
	}

	record CustomerResponse(UUID id, String email, String fullName, Role role, Instant createdAt,
			List<AccountResponse> accounts) {
	}

	@Transactional(readOnly = true)
	Stats stats() {
		return new Stats(users.countByRole(Role.CUSTOMER), accounts.count(), accounts.totalBalance(),
				transfers.countByCreatedAtAfter(Instant.now().minus(Duration.ofHours(24))));
	}

	@Transactional(readOnly = true)
	List<CustomerResponse> customers() {
		// Two queries in total, instead of one accounts query per user
		Map<UUID, List<AccountResponse>> accountsByOwner = accounts.findAllWithOwner()
			.stream()
			.collect(Collectors.groupingBy(account -> account.getOwner().getId(),
					Collectors.mapping(AccountResponse::from, Collectors.toList())));
		return users.findAllByOrderByCreatedAtDesc()
			.stream()
			.map(user -> new CustomerResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
					user.getCreatedAt(), accountsByOwner.getOrDefault(user.getId(), List.of())))
			.toList();
	}

	@Transactional
	AccountResponse setStatus(UUID accountId, AccountStatus status) {
		Account account = accounts.findByIdForUpdate(accountId)
			.orElseThrow(() -> ApiException.notFound("Account not found"));
		account.setStatus(status);
		return AccountResponse.from(account);
	}

}
