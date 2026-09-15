package dev.jyothsna.minibank.common;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

	private CurrentUser() {
	}

	/** The token subject is the user's id, set by {@code TokenService}. */
	public static UUID id(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
