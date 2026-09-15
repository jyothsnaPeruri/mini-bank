package dev.jyothsna.minibank.account;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.jyothsna.minibank.common.ApiException;
import dev.jyothsna.minibank.common.PageResponse;
import dev.jyothsna.minibank.user.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

	public static final int MAX_ACCOUNTS_PER_USER = 4;

	private final AccountRepository accounts;

	private final LedgerEntryRepository ledger;

	private final SecureRandom random = new SecureRandom();

	AccountService(AccountRepository accounts, LedgerEntryRepository ledger) {
		this.accounts = accounts;
		this.ledger = ledger;
	}

	@Transactional
	public Account openAccount(User owner, AccountType type, String nickname, BigDecimal openingDeposit,
			String depositDescription, Instant at) {
		if (accounts.countByOwnerId(owner.getId()) >= MAX_ACCOUNTS_PER_USER) {
			throw ApiException.conflict("account_limit",
					"You can have up to " + MAX_ACCOUNTS_PER_USER + " accounts");
		}
		Account account = accounts.save(new Account(newAccountNumber(), owner, type, nickname.strip(), at));
		if (openingDeposit.signum() > 0) {
			account.credit(openingDeposit);
			ledger.save(new LedgerEntry(account, null, EntryType.OPENING_DEPOSIT, openingDeposit, depositDescription,
					"Mini Bank", at));
		}
		return account;
	}

	@Transactional(readOnly = true)
	public List<AccountResponse> listForOwner(UUID ownerId) {
		return accounts.findByOwnerIdOrderByCreatedAt(ownerId).stream().map(AccountResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public AccountResponse getForOwner(UUID ownerId, UUID accountId) {
		return AccountResponse.from(findOwned(ownerId, accountId));
	}

	@Transactional(readOnly = true)
	public PayeeResponse lookupPayee(String accountNumber) {
		return accounts.findByAccountNumber(accountNumber)
			.map(PayeeResponse::from)
			.orElseThrow(() -> ApiException.notFound("No account found with number " + accountNumber));
	}

	public record HistoryFilter(String direction, Instant from, Instant to, String search) {
	}

	@Transactional(readOnly = true)
	public PageResponse<LedgerEntryResponse> history(UUID ownerId, UUID accountId, HistoryFilter filter, int page,
			int size) {
		Account account = findOwned(ownerId, accountId);
		PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		return PageResponse.from(ledger.findAll(matching(account.getId(), filter), pageRequest)
			.map(LedgerEntryResponse::from));
	}

	private static Specification<LedgerEntry> matching(UUID accountId, HistoryFilter filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("account").get("id"), accountId));
			if ("in".equalsIgnoreCase(filter.direction())) {
				predicates.add(cb.greaterThan(root.get("amount"), BigDecimal.ZERO));
			}
			else if ("out".equalsIgnoreCase(filter.direction())) {
				predicates.add(cb.lessThan(root.get("amount"), BigDecimal.ZERO));
			}
			if (filter.from() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
			}
			if (filter.to() != null) {
				predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
			}
			if (filter.search() != null && !filter.search().isBlank()) {
				String pattern = "%" + escapeLike(filter.search().strip().toLowerCase(Locale.ROOT)) + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("description")), pattern, '\\'),
						cb.like(cb.lower(root.get("counterparty")), pattern, '\\')));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private Account findOwned(UUID ownerId, UUID accountId) {
		return accounts.findById(accountId)
			.filter(account -> account.isOwnedBy(ownerId))
			.orElseThrow(() -> ApiException.notFound("Account not found"));
	}

	/** Random 10-digit number that doesn't start with 0. */
	private String newAccountNumber() {
		String number;
		do {
			number = String.valueOf(1_000_000_000L + (random.nextLong() & Long.MAX_VALUE) % 9_000_000_000L);
		}
		while (accounts.existsByAccountNumber(number));
		return number;
	}

}
