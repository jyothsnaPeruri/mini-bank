package dev.jyothsna.minibank.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.jyothsna.minibank.transfer.Transfer;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One line on an account statement. Entries are append-only: a balance is never changed without one.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY)
	private Transfer transfer;

	@Enumerated(EnumType.STRING)
	private EntryType type;

	/** Negative for money leaving the account. */
	private BigDecimal amount;

	private BigDecimal balanceAfter;

	private String description;

	private String counterparty;

	private Instant createdAt;

	protected LedgerEntry() {
	}

	public LedgerEntry(Account account, Transfer transfer, EntryType type, BigDecimal amount, String description,
			String counterparty, Instant createdAt) {
		this.account = account;
		this.transfer = transfer;
		this.type = type;
		this.amount = amount;
		this.balanceAfter = account.getBalance();
		this.description = description;
		this.counterparty = counterparty;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public Account getAccount() {
		return account;
	}

	/** Reading the id of a lazy association doesn't load the transfer row. */
	public UUID getTransferId() {
		return transfer == null ? null : transfer.getId();
	}

	public EntryType getType() {
		return type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public String getDescription() {
		return description;
	}

	public String getCounterparty() {
		return counterparty;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
