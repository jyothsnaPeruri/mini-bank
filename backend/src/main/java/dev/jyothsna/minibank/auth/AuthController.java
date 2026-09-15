package dev.jyothsna.minibank.auth;

import dev.jyothsna.minibank.auth.AuthDtos.AuthResponse;
import dev.jyothsna.minibank.auth.AuthDtos.LoginRequest;
import dev.jyothsna.minibank.auth.AuthDtos.RegisterRequest;
import dev.jyothsna.minibank.common.ApiException;
import dev.jyothsna.minibank.common.CurrentUser;
import dev.jyothsna.minibank.user.UserRepository;
import dev.jyothsna.minibank.user.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

	private final AuthService authService;

	private final UserRepository users;

	AuthController(AuthService authService, UserRepository users) {
		this.authService = authService;
		this.users = users;
	}

	@PostMapping("/api/auth/register")
	@ResponseStatus(HttpStatus.CREATED)
	AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/api/auth/login")
	AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@GetMapping("/api/me")
	UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return users.findById(CurrentUser.id(jwt))
			.map(UserResponse::from)
			.orElseThrow(() -> ApiException.unauthorized("Your session has ended. Please sign in again."));
	}

}
