package dev.jyothsna.minibank.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.jyothsna.minibank.account.Account;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transfers")
public class Transfer {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID initiatedBy;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Account fromAccount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Account toAccount;

	private BigDecimal amount;

	private String description;

	private String idempotencyKey;

	private Instant createdAt;

	protected Transfer() {
	}

	public Transfer(UUID initiatedBy, Account fromAccount, Account toAccount, BigDecimal amount, String description,
			String idempotencyKey, Instant createdAt) {
		this.initiatedBy = initiatedBy;
		this.fromAccount = fromAccount;
		this.toAccount = toAccount;
		this.amount = amount;
		this.description = description;
		this.idempotencyKey = idempotencyKey;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getInitiatedBy() {
		return initiatedBy;
	}

	public Account getFromAccount() {
		return fromAccount;
	}

	public Account getToAccount() {
		return toAccount;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getDescription() {
		return description;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
