package dev.jyothsna.minibank.auth;

import java.time.Instant;
import java.util.List;

import dev.jyothsna.minibank.config.AppProperties;
import dev.jyothsna.minibank.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
class TokenService {

	private final JwtEncoder encoder;

	private final AppProperties properties;

	TokenService(JwtEncoder encoder, AppProperties properties) {
		this.encoder = encoder;
		this.properties = properties;
	}

	String issue(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer("minibank")
			.issuedAt(now)
			.expiresAt(now.plus(properties.jwt().ttl()))
			.subject(user.getId().toString())
			.claim("email", user.getEmail())
			.claim("name", user.getFullName())
			.claim("roles", List.of(user.getRole().name()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
