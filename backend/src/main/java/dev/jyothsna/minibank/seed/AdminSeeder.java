package dev.jyothsna.minibank.seed;

import java.util.Locale;

import dev.jyothsna.minibank.config.AppProperties;
import dev.jyothsna.minibank.user.Role;
import dev.jyothsna.minibank.user.User;
import dev.jyothsna.minibank.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the admin user from ADMIN_EMAIL / ADMIN_PASSWORD, so no admin password is ever committed to git. */
@Component
class AdminSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

	private final AppProperties properties;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	AdminSeeder(AppProperties properties, UserRepository users, PasswordEncoder passwordEncoder) {
		this.properties = properties;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		String email = properties.admin().email();
		String password = properties.admin().password();
		if (email == null || email.isBlank() || password == null || password.isBlank()) {
			return;
		}
		String normalized = email.strip().toLowerCase(Locale.ROOT);
		if (!users.existsByEmail(normalized)) {
			users.save(new User(normalized, passwordEncoder.encode(password), "Administrator", Role.ADMIN));
			log.info("Created admin user {}", normalized);
		}
	}

}
