package dev.jyothsna.minibank.auth;

import dev.jyothsna.minibank.user.UserResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AuthDtos {

	private AuthDtos() {
	}

	record RegisterRequest(
			@NotBlank(message = "Enter your name") @Size(max = 100, message = "Name can be at most 100 characters") String fullName,
			@NotBlank(message = "Enter your email") @Email(message = "Enter a valid email") @Size(max = 255) String email,
			// BCrypt only uses the first 72 bytes of a password
			@NotBlank(message = "Choose a password") @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password) {
	}

	record LoginRequest(@NotBlank(message = "Enter your email") String email,
			@NotBlank(message = "Enter your password") String password) {
	}

	record AuthResponse(String token, UserResponse user) {
	}

}
