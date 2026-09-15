package dev.jyothsna.minibank.seed;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.jyothsna.minibank.account.Account;
import dev.jyothsna.minibank.account.AccountService;
import dev.jyothsna.minibank.account.AccountType;
import dev.jyothsna.minibank.transfer.TransferService;
import dev.jyothsna.minibank.user.Role;
import dev.jyothsna.minibank.user.User;
import dev.jyothsna.minibank.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a demo customer with two months of history, so visitors can try the app without signing up.
 * Everything goes through the real services, so the demo ledger follows the same rules as real use.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

	static final String DEMO_EMAIL = "demo@minibank.dev";

	static final String DEMO_PASSWORD = "Demo@1234";

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private final UserRepository users;

	private final AccountService accountService;

	private final TransferService transferService;

	private final PasswordEncoder passwordEncoder;

	DemoDataSeeder(UserRepository users, AccountService accountService, TransferService transferService,
			PasswordEncoder passwordEncoder) {
		this.users = users;
		this.accountService = accountService;
		this.transferService = transferService;
		this.passwordEncoder = passwordEncoder;
	}

	private record Payment(int daysAgo, Account from, Account to, String amount, String description) {
	}

	@Override
	public void run(ApplicationArguments args) {
		if (users.existsByEmail(DEMO_EMAIL)) {
			return;
		}
		Instant opened = daysAgo(62);

		User sam = customer("Sam Taylor", DEMO_EMAIL, DEMO_PASSWORD);
		User alex = customer("Alex Chen", "alex@minibank.dev", UUID.randomUUID().toString());
		User priya = customer("Priya Sharma", "priya@minibank.dev", UUID.randomUUID().toString());

		Account samEveryday = accountService.openAccount(sam, AccountType.EVERYDAY, "Everyday",
				new BigDecimal("5400.00"), "Opening deposit", opened);
		Account samSavings = accountService.openAccount(sam, AccountType.SAVINGS, "Holiday fund",
				new BigDecimal("6500.00"), "Opening deposit", opened);
		Account alexEveryday = accountService.openAccount(alex, AccountType.EVERYDAY, "Everyday",
				new BigDecimal("3100.00"), "Opening deposit", opened);
		Account priyaEveryday = accountService.openAccount(priya, AccountType.EVERYDAY, "Everyday",
				new BigDecimal("2750.00"), "Opening deposit", opened);

		List<Payment> history = List.of(
				new Payment(58, samEveryday, alexEveryday, "620.00", "Rent share - fortnight 1"),
				new Payment(55, samEveryday, samSavings, "400.00", "Monthly savings"),
				new Payment(51, priyaEveryday, samEveryday, "45.50", "Dinner - my share"),
				new Payment(44, samEveryday, alexEveryday, "620.00", "Rent share - fortnight 2"),
				new Payment(40, alexEveryday, samEveryday, "86.20", "Electricity bill split"),
				new Payment(33, samEveryday, priyaEveryday, "129.00", "Concert tickets"),
				new Payment(30, samEveryday, alexEveryday, "620.00", "Rent share - fortnight 3"),
				new Payment(25, samEveryday, samSavings, "400.00", "Monthly savings"),
				new Payment(19, priyaEveryday, samEveryday, "250.00", "Weekend trip - my share"),
				new Payment(16, samEveryday, alexEveryday, "620.00", "Rent share - fortnight 4"),
				new Payment(9, alexEveryday, samEveryday, "38.75", "Groceries"),
				new Payment(4, samSavings, samEveryday, "300.00", "Top up for flights"),
				new Payment(2, samEveryday, alexEveryday, "620.00", "Rent share - fortnight 5"));

		for (int i = 0; i < history.size(); i++) {
			Payment payment = history.get(i);
			// Spread payments across the day so the history doesn't show identical times
			Instant at = daysAgo(payment.daysAgo()).minus(Duration.ofMinutes((i * 137L) % 540));
			transferService.postTransfer(payment.from().getOwner().getId(), payment.from().getId(),
					payment.to().getId(), new BigDecimal(payment.amount()), payment.description(), "seed-" + i, at);
		}
		log.info("Demo data created - sign in as {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
	}

	private User customer(String name, String email, String password) {
		return users.save(new User(email, passwordEncoder.encode(password), name, Role.CUSTOMER));
	}

	private static Instant daysAgo(int days) {
		return Instant.now().minus(Duration.ofDays(days));
	}

}
