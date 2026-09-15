package dev.jyothsna.minibank.admin;

import com.jayway.jsonpath.JsonPath;
import dev.jyothsna.minibank.IntegrationTest;
import dev.jyothsna.minibank.user.Role;
import dev.jyothsna.minibank.user.User;
import dev.jyothsna.minibank.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void customersCannotUseAdminEndpoints() throws Exception {
		Customer sam = register("Sam Taylor", "sam@example.com");

		mvc.perform(get("/api/admin/customers").header("Authorization", sam.bearer())).andExpect(status().isForbidden());
	}

	@Test
	void adminCanSeeCustomersAndFreezeAnAccount() throws Exception {
		Customer sam = register("Sam Taylor", "sam@example.com");
		Customer alex = register("Alex Chen", "alex@example.com");
		String admin = adminToken();

		mvc.perform(get("/api/admin/customers").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3));

		mvc.perform(patch("/api/admin/accounts/{id}", sam.accountId()).header("Authorization", admin)
			.contentType(APPLICATION_JSON)
			.content("{\"status\": \"FROZEN\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FROZEN"));

		transfer(sam, sam.accountId(), alex.accountNumber(), "10.00", "key-1").andExpect(status().isConflict());
	}

	private String adminToken() throws Exception {
		users.save(new User("admin@example.com", passwordEncoder.encode("admin-password"), "Administrator", Role.ADMIN));
		String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("""
				{"email": "admin@example.com", "password": "admin-password"}
				"""))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.token");
	}

}
