package dev.jyothsna.minibank.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(UUID id, UUID transferId, EntryType type, BigDecimal amount, BigDecimal balanceAfter,
		String description, String counterparty, Instant createdAt) {

	static LedgerEntryResponse from(LedgerEntry entry) {
		return new LedgerEntryResponse(entry.getId(), entry.getTransferId(), entry.getType(), entry.getAmount(),
				entry.getBalanceAfter(), entry.getDescription(), entry.getCounterparty(), entry.getCreatedAt());
	}

}
