package dev.jyothsna.minibank.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import dev.jyothsna.minibank.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Drives the MCP endpoint the way an AI client does — JSON-RPC over HTTP — against a real database, and checks the
 * rules that matter when a model can move money: it acts only as the signed-in customer, a prepared transfer moves
 * nothing, a confirmed one moves money exactly once, and a code is useless to anyone else.
 */
class McpIntegrationTest extends IntegrationTest {

	private int nextId = 1;

	private MockHttpServletResponse rpc(Customer customer, String method, String params) throws Exception {
		MockHttpServletRequestBuilder request = post("/mcp").contentType(APPLICATION_JSON)
			.accept(APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.header("MCP-Protocol-Version", "2025-06-18")
			.content("""
					{"jsonrpc": "2.0", "id": %d, "method": "%s", "params": %s}
					""".formatted(nextId++, method, params));
		if (customer != null) {
			request.header(AUTHORIZATION, customer.bearer());
		}
		return mvc.perform(request).andReturn().getResponse();
	}

	/** Calls a tool; returns the parsed result envelope ({@code isError}, {@code content}). */
	private Map<String, Object> call(Customer customer, String tool, String arguments) throws Exception {
		MockHttpServletResponse response = rpc(customer, "tools/call",
				"{\"name\": \"%s\", \"arguments\": %s}".formatted(tool, arguments));
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		return JsonPath.read(response.getContentAsString(), "$.result");
	}

	private static boolean isError(Map<String, Object> result) {
		return Boolean.TRUE.equals(result.get("isError"));
	}

	private static String text(Map<String, Object> result) {
		return JsonPath.read(result, "$.content[0].text");
	}

	@Test
	void requiresSignIn() throws Exception {
		assertThat(rpc(null, "tools/list", "{}").getStatus()).isEqualTo(401);
	}

	@Test
	void listsTheSixToolsWithSafetyHints() throws Exception {
		Customer alice = register("Alice Smith", "alice@example.com");
		String body = rpc(alice, "tools/list", "{}").getContentAsString();

		List<String> names = JsonPath.read(body, "$.result.tools[*].name");
		assertThat(names).containsExactlyInAnyOrder("list_accounts", "get_account", "recent_transactions", "find_payee",
				"prepare_transfer", "confirm_transfer");
		List<Boolean> destructive = JsonPath.read(body, "$.result.tools[?(@.name == 'confirm_transfer')].annotations.destructiveHint");
		List<Boolean> readOnly = JsonPath.read(body, "$.result.tools[?(@.name == 'list_accounts')].annotations.readOnlyHint");
		assertThat(destructive).containsExactly(true);
		assertThat(readOnly).containsExactly(true);
	}

	@Test
	void customersOnlySeeTheirOwnAccounts() throws Exception {
		Customer alice = register("Alice Smith", "alice@example.com");
		Customer bob = register("Bob Jones", "bob@example.com");

		String aliceAccounts = text(call(alice, "list_accounts", "{}"));
		assertThat(aliceAccounts).contains(alice.accountNumber()).doesNotContain(bob.accountNumber());

		Map<String, Object> peek = call(alice, "get_account", "{\"accountId\": \"%s\"}".formatted(bob.accountId()));
		assertThat(isError(peek)).isTrue();
		assertThat(text(peek)).contains("Account not found");

		Map<String, Object> history = call(alice, "recent_transactions",
				"{\"accountId\": \"%s\"}".formatted(bob.accountId()));
		assertThat(isError(history)).isTrue();
	}

	@Test
	void prepareMovesNoMoneyAndConfirmMovesItExactlyOnce() throws Exception {
		Customer alice = register("Alice Smith", "alice@example.com");
		Customer bob = register("Bob Jones", "bob@example.com");

		Map<String, Object> prepared = call(alice, "prepare_transfer", """
				{"fromAccountId": "%s", "toAccountNumber": "%s", "amount": 250, "description": "Dinner"}
				""".formatted(alice.accountId(), bob.accountNumber()));
		assertThat(isError(prepared)).as(text(prepared)).isFalse();
		String code = JsonPath.read(text(prepared), "$.confirmationCode");
		assertThat(code).startsWith("MB-");
		assertThat(text(prepared)).contains("Bob J.");
		assertThat(balanceOf(alice.accountId())).isEqualByComparingTo("1000.00");
		assertThat(balanceOf(bob.accountId())).isEqualByComparingTo("1000.00");

		Map<String, Object> sent = call(alice, "confirm_transfer", "{\"confirmationCode\": \"%s\"}".formatted(code));
		assertThat(isError(sent)).as(text(sent)).isFalse();
		assertThat((Boolean) JsonPath.read(text(sent), "$.alreadyConfirmed")).isFalse();
		assertThat(balanceOf(alice.accountId())).isEqualByComparingTo("750.00");
		assertThat(balanceOf(bob.accountId())).isEqualByComparingTo("1250.00");

		// A model that retries, or a customer who says "yes" twice, must not pay twice.
		Map<String, Object> again = call(alice, "confirm_transfer", "{\"confirmationCode\": \"%s\"}".formatted(code));
		assertThat((Boolean) JsonPath.read(text(again), "$.alreadyConfirmed")).isTrue();
		assertThat(balanceOf(alice.accountId())).isEqualByComparingTo("750.00");
		assertThat(balanceOf(bob.accountId())).isEqualByComparingTo("1250.00");
	}

	@Test
	void aConfirmationCodeIsUselessToAnotherCustomer() throws Exception {
		Customer alice = register("Alice Smith", "alice@example.com");
		Customer bob = register("Bob Jones", "bob@example.com");
		String code = JsonPath.read(text(call(alice, "prepare_transfer", """
				{"fromAccountId": "%s", "toAccountNumber": "%s", "amount": 100}
				""".formatted(alice.accountId(), bob.accountNumber()))), "$.confirmationCode");

		Map<String, Object> stolen = call(bob, "confirm_transfer", "{\"confirmationCode\": \"%s\"}".formatted(code));
		assertThat(isError(stolen)).isTrue();
		assertThat(text(stolen)).contains("invalid or has expired");
		assertThat(balanceOf(alice.accountId())).isEqualByComparingTo("1000.00");
	}

	@Test
	void prepareRefusesWhatTheTransferItselfWouldRefuse() throws Exception {
		Customer alice = register("Alice Smith", "alice@example.com");
		Customer bob = register("Bob Jones", "bob@example.com");

		Map<String, Object> tooMuch = call(alice, "prepare_transfer", """
				{"fromAccountId": "%s", "toAccountNumber": "%s", "amount": 1000.01}
				""".formatted(alice.accountId(), bob.accountNumber()));
		assertThat(isError(tooMuch)).isTrue();
		assertThat(text(tooMuch)).contains("Insufficient funds");

		Map<String, Object> overLimit = call(alice, "prepare_transfer", """
				{"fromAccountId": "%s", "toAccountNumber": "%s", "amount": 20000}
				""".formatted(alice.accountId(), bob.accountNumber()));
		assertThat(isError(overLimit)).isTrue();
		assertThat(text(overLimit)).contains("limited to $10,000");

		Map<String, Object> toSelf = call(alice, "prepare_transfer", """
				{"fromAccountId": "%s", "toAccountNumber": "%s", "amount": 5}
				""".formatted(alice.accountId(), alice.accountNumber()));
		assertThat(isError(toSelf)).isTrue();
		assertThat(text(toSelf)).contains("same account");
	}

}
