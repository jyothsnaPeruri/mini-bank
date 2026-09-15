package dev.jyothsna.minibank.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Cors cors, Demo demo, Admin admin) {

	public record Jwt(String secret, Duration ttl) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

	public record Demo(boolean enabled) {
	}

	public record Admin(String email, String password) {
	}

}
