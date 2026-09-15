package dev.jyothsna.minibank;

import java.math.BigDecimal;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole app against a real PostgreSQL database (minibank_test) and starts every test from empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("truncate table ledger_entries, transfers, accounts, users cascade");
	}

	protected record Customer(UUID userId, String token, UUID accountId, String accountNumber) {

		public String bearer() {
			return "Bearer " + token;
		}

	}

	protected Customer register(String fullName, String email) throws Exception {
		String body = mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
				{"fullName": "%s", "email": "%s", "password": "password123"}
				""".formatted(fullName, email)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = JsonPath.read(body, "$.token");
		String accounts = mvc.perform(get("/api/accounts").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new Customer(UUID.fromString(JsonPath.read(body, "$.user.id")), token,
				UUID.fromString(JsonPath.read(accounts, "$[0].id")), JsonPath.read(accounts, "$[0].accountNumber"));
	}

	protected ResultActions transfer(Customer payer, UUID fromAccountId, String toAccountNumber, String amount,
			String idempotencyKey) throws Exception {
		return mvc.perform(post("/api/transfers").header(AUTHORIZATION, payer.bearer())
			.header("Idempotency-Key", idempotencyKey)
			.contentType(APPLICATION_JSON)
			.content("""
					{"fromAccountId": "%s", "toAccountNumber": "%s", "amount": %s, "description": "Test payment"}
					""".formatted(fromAccountId, toAccountNumber, amount)));
	}

	protected BigDecimal balanceOf(UUID accountId) {
		return jdbc.queryForObject("select balance from accounts where id = ?", BigDecimal.class, accountId);
	}

}
