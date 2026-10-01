package dev.jyothsna.minibank.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import dev.jyothsna.minibank.transfer.TransferRequest;
import org.junit.jupiter.api.Test;

class PendingTransfersTest {

	/** A clock the test can move forward. */
	static final class MutableClock extends Clock {

		Instant now = Instant.parse("2026-10-01T00:00:00Z");

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

	}

	private final MutableClock clock = new MutableClock();

	private final PendingTransfers pending = new PendingTransfers(clock);

	private final UUID alice = UUID.randomUUID();

	private final TransferRequest request = new TransferRequest(UUID.randomUUID(), "1234567890", new BigDecimal("10.00"),
			null);

	@Test
	void aCodeWorksUntilItExpires() {
		String code = pending.hold(alice, request, "Bob J.").code();
		clock.now = clock.now.plus(PendingTransfers.TTL).minusSeconds(1);
		assertThat(pending.find(alice, code)).isPresent();
		clock.now = clock.now.plusSeconds(1);
		assertThat(pending.find(alice, code)).isEmpty();
	}

	@Test
	void codesAreCaseAndSpaceTolerant() {
		String code = pending.hold(alice, request, "Bob J.").code();
		assertThat(pending.find(alice, "  " + code.toLowerCase() + " ")).isPresent();
	}

	@Test
	void aCustomerCannotHoardPreviews() {
		for (int i = 0; i < PendingTransfers.MAX_PENDING_PER_CUSTOMER; i++) {
			pending.hold(alice, request, "Bob J.");
		}
		assertThatThrownBy(() -> pending.hold(alice, request, "Bob J.")).isInstanceOf(IllegalStateException.class);
		// ...but expired ones stop counting.
		clock.now = clock.now.plus(PendingTransfers.TTL);
		assertThat(pending.hold(alice, request, "Bob J.").code()).startsWith("MB-");
	}

}
