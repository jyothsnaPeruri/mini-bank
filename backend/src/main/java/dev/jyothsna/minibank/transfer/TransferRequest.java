package dev.jyothsna.minibank.transfer;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TransferRequest(
		@NotNull(message = "Choose an account to pay from") UUID fromAccountId,
		@NotNull(message = "Enter an account number") @Pattern(regexp = "\\d{10}", message = "Account numbers are 10 digits") String toAccountNumber,
		@NotNull(message = "Enter an amount")
		@DecimalMin(value = "0.01", message = "Amount must be at least $0.01")
		@DecimalMax(value = "10000.00", message = "Transfers are limited to $10,000")
		@Digits(integer = 5, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal amount,
		@Size(max = 140, message = "Description can be at most 140 characters") String description) {
}
