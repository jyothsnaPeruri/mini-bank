package dev.jyothsna.minibank.transfer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.jyothsna.minibank.IntegrationTest;
import dev.jyothsna.minibank.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferIntegrationTest extends IntegrationTest {

	@Autowired
	TransferService transferService;

	@Test
	void transferMovesMoneyAndRecordsBothSidesInTheLedger() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer bob = register("Bob Smith", "bob@example.com");

		transfer(alice, alice.accountId(), bob.accountNumber(), "250.00", "key-1")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.amount").value(250.00))
			.andExpect(jsonPath("$.toAccountHolder").value("Bob Smith"));

		assertThat(balanceOf(alice.accountId())).isEqualByComparingTo("750.00");
		assertThat(balanceOf(bob.accountId())).isEqualByComparingTo("1250.00");

		mvc.perform(get("/api/accounts/{id}/transactions", bob.accountId()).header("Authorization", bob.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].type").value("TRANSFER_IN"))
			.andExpect(jsonPath("$.content[0].counterparty").value("Alice Nguyen"))
			.andExpect(jsonPath("$.content[0].balanceAfter").value(1250.00));
	}

	@Test
	void cannotSendMoreThanTheBalance() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer bob = register("Bob Smith", "bob@example.com");

		transfer(alice, alice.accountId(), bob.accountNumber(), "1000.01", "key-1")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("insufficient_funds"));

		assertThat(balanceOf(alice.accountId())).isEqualByComparingTo("1000.00");
		assertThat(balanceOf(bob.accountId())).isEqualByComparingTo("1000.00");
	}

	@Test
	void cannotSpendFromSomeoneElsesAccount() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer mallory = register("Mallory Jones", "mallory@example.com");

		transfer(mallory, alice.accountId(), mallory.accountNumber(), "500.00", "key-1")
			.andExpect(status().isNotFound());

		assertThat(balanceOf(alice.accountId())).isEqualByComparingTo("1000.00");
	}

	@Test
	void retryingWithTheSameIdempotencyKeyOnlyMovesMoneyOnce() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer bob = register("Bob Smith", "bob@example.com");

		String firstId = com.jayway.jsonpath.JsonPath.read(
				transfer(alice, alice.accountId(), bob.accountNumber(), "100.00", "double-click")
					.andExpect(status().isCreated())
					.andReturn()
					.getResponse()
					.getContentAsString(),
				"$.id");

		transfer(alice, alice.accountId(), bob.accountNumber(), "100.00", "double-click")
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotent-Replayed", "true"))
			.andExpect(jsonPath("$.id").value(firstId));

		assertThat(balanceOf(alice.accountId())).isEqualByComparingTo("900.00");
		assertThat(balanceOf(bob.accountId())).isEqualByComparingTo("1100.00");
	}

	@Test
	void reusingAnIdempotencyKeyForADifferentTransferIsRejected() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer bob = register("Bob Smith", "bob@example.com");

		transfer(alice, alice.accountId(), bob.accountNumber(), "100.00", "key-1").andExpect(status().isCreated());
		transfer(alice, alice.accountId(), bob.accountNumber(), "999.00", "key-1")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("idempotency_key_reused"));

		assertThat(balanceOf(alice.accountId())).isEqualByComparingTo("900.00");
	}

	@Test
	void frozenAccountsCannotSendMoney() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer bob = register("Bob Smith", "bob@example.com");
		jdbc.update("update accounts set status = 'FROZEN' where id = ?", alice.accountId());

		transfer(alice, alice.accountId(), bob.accountNumber(), "10.00", "key-1")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("account_frozen"));
	}

	@Test
	void invalidAmountsAreRejected() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer bob = register("Bob Smith", "bob@example.com");

		transfer(alice, alice.accountId(), bob.accountNumber(), "-5.00", "key-1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.amount").exists());
		transfer(alice, alice.accountId(), bob.accountNumber(), "1.005", "key-2")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.amount").exists());
	}

	@Test
	void concurrentTransfersNeverOverdrawTheAccount() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer bob = register("Bob Smith", "bob@example.com");
		int attempts = 10;
		TransferRequest request = new TransferRequest(alice.accountId(), bob.accountNumber(), new BigDecimal("150.00"),
				"Race");

		// Fire all 10 transfers at the same instant; $1000 only covers 6 of them
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CountDownLatch startGun = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			String key = "race-" + i;
			results.add(pool.submit(() -> {
				startGun.await();
				try {
					transferService.transfer(alice.userId(), request, key);
					return true;
				}
				catch (ApiException ex) {
					assertThat(ex.getCode()).isEqualTo("insufficient_funds");
					return false;
				}
			}));
		}
		startGun.countDown();
		int succeeded = 0;
		for (Future<Boolean> result : results) {
			succeeded += result.get() ? 1 : 0;
		}
		pool.shutdown();

		assertThat(succeeded).isEqualTo(6);
		assertThat(balanceOf(alice.accountId())).isEqualByComparingTo("100.00");
		assertThat(balanceOf(bob.accountId())).isEqualByComparingTo("1900.00");
	}

	@Test
	void historyCanBeFilteredByDirectionAndText() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer bob = register("Bob Smith", "bob@example.com");
		transfer(alice, alice.accountId(), bob.accountNumber(), "20.00", "key-1").andExpect(status().isCreated());
		transfer(bob, bob.accountId(), alice.accountNumber(), "5.00", "key-2").andExpect(status().isCreated());

		mvc.perform(get("/api/accounts/{id}/transactions", alice.accountId()).param("direction", "out")
			.header("Authorization", alice.bearer()))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].amount").value(-20.00));

		mvc.perform(get("/api/accounts/{id}/transactions", alice.accountId()).param("q", "welcome")
			.header("Authorization", alice.bearer()))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].type").value("OPENING_DEPOSIT"));
	}

	@Test
	void recentPayeesListsOtherPeopleMostRecentFirst() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer bob = register("Bob Smith", "bob@example.com");
		Customer carol = register("Carol White", "carol@example.com");
		transfer(alice, alice.accountId(), bob.accountNumber(), "10.00", "key-1").andExpect(status().isCreated());
		transfer(alice, alice.accountId(), carol.accountNumber(), "10.00", "key-2").andExpect(status().isCreated());
		transfer(alice, alice.accountId(), bob.accountNumber(), "10.00", "key-3").andExpect(status().isCreated());

		mvc.perform(get("/api/transfers/recent-payees").header("Authorization", alice.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].holderName").value("Bob S."))
			.andExpect(jsonPath("$[1].holderName").value("Carol W."));
	}

	@Test
	void payeeLookupShowsAMaskedName() throws Exception {
		Customer alice = register("Alice Nguyen", "alice@example.com");
		Customer bob = register("Bob Smith", "bob@example.com");

		mvc.perform(get("/api/accounts/lookup").param("number", bob.accountNumber())
			.header("Authorization", alice.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.holderName").value("Bob S."));
	}

}
