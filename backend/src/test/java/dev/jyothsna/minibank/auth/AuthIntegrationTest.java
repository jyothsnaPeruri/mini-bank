package dev.jyothsna.minibank.auth;

import dev.jyothsna.minibank.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

	@Test
	void newCustomerGetsAnEverydayAccountWithWelcomeCredit() throws Exception {
		Customer sam = register("Sam Taylor", "sam@example.com");

		assertThat(balanceOf(sam.accountId())).isEqualByComparingTo("1000.00");
		assertThat(sam.accountNumber()).matches("\\d{10}");
	}

	@Test
	void emailsAreUniqueIgnoringCase() throws Exception {
		register("Sam Taylor", "sam@example.com");

		mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
				{"fullName": "Someone Else", "email": "SAM@example.com", "password": "password123"}
				"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("email_taken"));
	}

	@Test
	void invalidRegistrationReportsEachField() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
				{"fullName": "", "email": "not-an-email", "password": "short"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.fullName").exists())
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void loginReturnsATokenThatWorksOnTheApi() throws Exception {
		register("Sam Taylor", "sam@example.com");

		String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("""
				{"email": "sam@example.com", "password": "password123"}
				"""))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = com.jayway.jsonpath.JsonPath.read(body, "$.token");

		mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Sam Taylor"))
			.andExpect(jsonPath("$.role").value("CUSTOMER"));
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		register("Sam Taylor", "sam@example.com");

		mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("""
				{"email": "sam@example.com", "password": "wrong-password"}
				"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Incorrect email or password"));
	}

	@Test
	void apiRequiresAValidToken() throws Exception {
		mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/accounts").header("Authorization", "Bearer not-a-real-token"))
			.andExpect(status().isUnauthorized());
	}

}
