package dev.jyothsna.minibank.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpenAccountRequest(
		@NotNull(message = "Choose an account type") AccountType type,
		@NotBlank(message = "Give the account a name") @Size(max = 50, message = "Name can be at most 50 characters") String nickname) {
}
