package dev.jyothsna.minibank.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import dev.jyothsna.minibank.account.Account;
import dev.jyothsna.minibank.account.AccountRepository;
import dev.jyothsna.minibank.account.EntryType;
import dev.jyothsna.minibank.account.LedgerEntry;
import dev.jyothsna.minibank.account.LedgerEntryRepository;
import dev.jyothsna.minibank.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferService {

	private final AccountRepository accounts;

	private final TransferRepository transfers;

	private final LedgerEntryRepository ledger;

	private final TransactionTemplate transaction;

	TransferService(AccountRepository accounts, TransferRepository transfers, LedgerEntryRepository ledger,
			TransactionTemplate transaction) {
		this.accounts = accounts;
		this.transfers = transfers;
		this.ledger = ledger;
		this.transaction = transaction;
	}

	public record Outcome(TransferResponse transfer, boolean replayed) {
	}

	/**
	 * Moves money on behalf of a customer. Calling this again with the same idempotency key (a double-click,
	 * a network retry) returns the original transfer instead of moving the money twice.
	 */
	public Outcome transfer(UUID userId, TransferRequest request, String idempotencyKey) {
		Optional<Transfer> previous = transfers.findByIdempotencyKey(userId, idempotencyKey);
		if (previous.isPresent()) {
			return replay(previous.get(), request);
		}
		try {
			Transfer created = transaction.execute(status -> {
				UUID toAccountId = accounts.findIdByAccountNumber(request.toAccountNumber())
					.orElseThrow(() -> ApiException.notFound("No account found with number " + request.toAccountNumber()));
				return postTransfer(userId, request.fromAccountId(), toAccountId, request.amount().setScale(2),
						blankToNull(request.description()), idempotencyKey, Instant.now());
			});
			return new Outcome(TransferResponse.from(Objects.requireNonNull(created)), false);
		}
		catch (DataIntegrityViolationException ex) {
			// Two requests with the same key raced; the unique constraint let exactly one commit.
			Transfer winner = transfers.findByIdempotencyKey(userId, idempotencyKey).orElseThrow(() -> ex);
			return replay(winner, request);
		}
	}

	/**
	 * The money movement itself: one database transaction that locks both accounts, checks the rules, updates
	 * both balances and writes the ledger. Either all of it is saved or none of it is.
	 */
	@Transactional
	public Transfer postTransfer(UUID initiatedBy, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
			String description, String idempotencyKey, Instant at) {
		if (fromAccountId.equals(toAccountId)) {
			throw ApiException.badRequest("same_account", "You can't transfer money to the same account");
		}

		// Always lock in the same (id) order, so A->B and B->A running at once can't deadlock each other
		boolean fromFirst = fromAccountId.compareTo(toAccountId) < 0;
		Account first = lockAccount(fromFirst ? fromAccountId : toAccountId);
		Account second = lockAccount(fromFirst ? toAccountId : fromAccountId);
		Account from = fromFirst ? first : second;
		Account to = fromFirst ? second : first;

		// Someone else's account is reported as "not found" so account ids can't be probed
		if (!from.isOwnedBy(initiatedBy)) {
			throw ApiException.notFound("Account not found");
		}
		if (!from.isActive()) {
			throw ApiException.conflict("account_frozen", "Your account is frozen. Please contact support.");
		}
		if (!to.isActive()) {
			throw ApiException.conflict("account_frozen", "The receiving account can't accept payments right now.");
		}
		if (from.getBalance().compareTo(amount) < 0) {
			throw ApiException.unprocessable("insufficient_funds", "Insufficient funds in " + from.getNickname());
		}

		from.debit(amount);
		to.credit(amount);

		// Between your own accounts, the other side is named by account ("Holiday fund") rather than by person
		boolean ownAccounts = to.isOwnedBy(initiatedBy);
		String toLabel = ownAccounts ? to.getNickname() : to.getOwner().getFullName();
		String fromLabel = ownAccounts ? from.getNickname() : from.getOwner().getFullName();

		Transfer transfer = transfers.save(new Transfer(initiatedBy, from, to, amount, description, idempotencyKey, at));
		ledger.save(new LedgerEntry(from, transfer, EntryType.TRANSFER_OUT, amount.negate(), description, toLabel, at));
		ledger.save(new LedgerEntry(to, transfer, EntryType.TRANSFER_IN, amount, description, fromLabel, at));
		return transfer;
	}

	private Account lockAccount(UUID id) {
		return accounts.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Account not found"));
	}

	private Outcome replay(Transfer previous, TransferRequest request) {
		boolean samePayload = previous.getFromAccount().getId().equals(request.fromAccountId())
				&& previous.getToAccount().getAccountNumber().equals(request.toAccountNumber())
				&& previous.getAmount().compareTo(request.amount()) == 0;
		if (!samePayload) {
			throw ApiException.conflict("idempotency_key_reused",
					"This Idempotency-Key was already used for a different transfer");
		}
		return new Outcome(TransferResponse.from(previous), true);
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
