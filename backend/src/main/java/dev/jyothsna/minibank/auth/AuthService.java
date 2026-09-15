package dev.jyothsna.minibank.auth;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

import dev.jyothsna.minibank.account.AccountService;
import dev.jyothsna.minibank.account.AccountType;
import dev.jyothsna.minibank.auth.AuthDtos.AuthResponse;
import dev.jyothsna.minibank.auth.AuthDtos.LoginRequest;
import dev.jyothsna.minibank.auth.AuthDtos.RegisterRequest;
import dev.jyothsna.minibank.common.ApiException;
import dev.jyothsna.minibank.user.Role;
import dev.jyothsna.minibank.user.User;
import dev.jyothsna.minibank.user.UserRepository;
import dev.jyothsna.minibank.user.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

	/** Every new customer starts with some play money so the demo is usable straight away. */
	static final BigDecimal WELCOME_CREDIT = new BigDecimal("1000.00");

	private final UserRepository users;

	private final AccountService accountService;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokenService;

	AuthService(UserRepository users, AccountService accountService, PasswordEncoder passwordEncoder,
			TokenService tokenService) {
		this.users = users;
		this.accountService = accountService;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
	}

	@Transactional
	AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("email_taken", "An account with this email already exists");
		}
		User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName().strip(),
				Role.CUSTOMER));
		accountService.openAccount(user, AccountType.EVERYDAY, "Everyday", WELCOME_CREDIT, "Welcome credit",
				Instant.now());
		return new AuthResponse(tokenService.issue(user), UserResponse.from(user));
	}

	@Transactional(readOnly = true)
	AuthResponse login(LoginRequest request) {
		User user = users.findByEmail(normalizeEmail(request.email()))
			.filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
			// Same message whether the email or the password was wrong, so emails can't be discovered
			.orElseThrow(() -> ApiException.unauthorized("Incorrect email or password"));
		return new AuthResponse(tokenService.issue(user), UserResponse.from(user));
	}

	static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
