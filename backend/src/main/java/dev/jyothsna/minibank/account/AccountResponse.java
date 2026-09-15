package dev.jyothsna.minibank.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String accountNumber, AccountType type, String nickname, BigDecimal balance,
		String currency, AccountStatus status, Instant createdAt) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(account.getId(), account.getAccountNumber(), account.getType(), account.getNickname(),
				account.getBalance(), account.getCurrency(), account.getStatus(), account.getCreatedAt());
	}

}
