package dev.jyothsna.minibank.mcp;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.jyothsna.minibank.transfer.TransferRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Transfers an AI assistant has prepared but the customer has not yet confirmed.
 *
 * <p>
 * Moving money takes two tool calls on purpose. {@code prepare_transfer} validates everything and returns a
 * confirmation code without touching any balance; only {@code confirm_transfer} with that code moves money. That gives
 * the assistant a natural point to show the customer exactly what will happen and wait for a yes, and it means a model
 * that misreads an instruction can at worst produce a preview.
 *
 * <p>
 * Codes are single-customer (another customer's code is simply not found), expire after five minutes, and are kept in
 * memory: the app runs as one instance, and a restart losing a pending preview is harmless — the customer prepares it
 * again. Running several instances would need a shared store such as the database or Redis.
 */
@Component
class PendingTransfers {

	static final Duration TTL = Duration.ofMinutes(5);

	static final int MAX_PENDING_PER_CUSTOMER = 5;

	/** No 0/O or 1/I, so a code read aloud or retyped by a person survives. */
	private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	record Pending(String code, UUID customerId, TransferRequest request, String payeeName, Instant expiresAt) {
	}

	private final Map<String, Pending> byCode = new ConcurrentHashMap<>();

	private final SecureRandom random = new SecureRandom();

	private final Clock clock;

	@Autowired
	PendingTransfers() {
		this(Clock.systemUTC());
	}

	PendingTransfers(Clock clock) {
		this.clock = clock;
	}

	Pending hold(UUID customerId, TransferRequest request, String payeeName) {
		purgeExpired();
		long open = byCode.values().stream().filter(p -> p.customerId().equals(customerId)).count();
		if (open >= MAX_PENDING_PER_CUSTOMER) {
			throw new IllegalStateException("Too many unconfirmed transfers. Confirm or wait for some to expire.");
		}
		Pending pending = new Pending(newCode(), customerId, request, payeeName, clock.instant().plus(TTL));
		byCode.put(pending.code(), pending);
		return pending;
	}

	/** Empty unless the code exists, belongs to this customer and has not expired. */
	Optional<Pending> find(UUID customerId, String code) {
		if (code == null) {
			return Optional.empty();
		}
		Pending pending = byCode.get(code.strip().toUpperCase());
		if (pending == null || !pending.customerId().equals(customerId) || isExpired(pending)) {
			return Optional.empty();
		}
		return Optional.of(pending);
	}

	private boolean isExpired(Pending pending) {
		return !clock.instant().isBefore(pending.expiresAt());
	}

	private void purgeExpired() {
		byCode.values().removeIf(this::isExpired);
	}

	private String newCode() {
		StringBuilder code = new StringBuilder("MB-");
		for (int i = 0; i < 8; i++) {
			code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
		}
		return code.toString();
	}

}
