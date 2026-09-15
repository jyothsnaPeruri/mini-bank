package dev.jyothsna.minibank.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, String fromAccountNumber, String toAccountNumber, String toAccountHolder,
		BigDecimal amount, String description, Instant createdAt) {

	static TransferResponse from(Transfer transfer) {
		return new TransferResponse(transfer.getId(), transfer.getFromAccount().getAccountNumber(),
				transfer.getToAccount().getAccountNumber(), transfer.getToAccount().getOwner().getFullName(),
				transfer.getAmount(), transfer.getDescription(), transfer.getCreatedAt());
	}

}
