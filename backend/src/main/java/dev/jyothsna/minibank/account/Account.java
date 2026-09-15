package dev.jyothsna.minibank.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.jyothsna.minibank.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String accountNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private User owner;

	@Enumerated(EnumType.STRING)
	private AccountType type;

	private String nickname;

	private BigDecimal balance;

	private String currency;

	@Enumerated(EnumType.STRING)
	private AccountStatus status;

	private Instant createdAt;

	protected Account() {
	}

	public Account(String accountNumber, User owner, AccountType type, String nickname, Instant createdAt) {
		this.accountNumber = accountNumber;
		this.owner = owner;
		this.type = type;
		this.nickname = nickname;
		this.balance = BigDecimal.ZERO.setScale(2);
		this.currency = "AUD";
		this.status = AccountStatus.ACTIVE;
		this.createdAt = createdAt;
	}

	public void credit(BigDecimal amount) {
		balance = balance.add(amount);
	}

	public void debit(BigDecimal amount) {
		if (balance.compareTo(amount) < 0) {
			throw new IllegalStateException("Debit would make account " + accountNumber + " negative");
		}
		balance = balance.subtract(amount);
	}

	public boolean isActive() {
		return status == AccountStatus.ACTIVE;
	}

	public boolean isOwnedBy(UUID userId) {
		return owner.getId().equals(userId);
	}

	public void setStatus(AccountStatus status) {
		this.status = status;
	}

	public UUID getId() {
		return id;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public User getOwner() {
		return owner;
	}

	public AccountType getType() {
		return type;
	}

	public String getNickname() {
		return nickname;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public String getCurrency() {
		return currency;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
