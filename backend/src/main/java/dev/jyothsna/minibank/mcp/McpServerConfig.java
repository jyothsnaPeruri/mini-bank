package dev.jyothsna.minibank.mcp;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Exposes the banking tools to AI assistants over MCP at {@code /mcp}.
 *
 * <p>
 * The endpoint sits behind the same Spring Security filter chain as the REST API, so a request only reaches it with a
 * valid JWT. The transport is replaced here only to add a context extractor: it copies the authenticated user's id
 * out of the verified token and into the MCP request context, which is how each tool learns who it is acting for.
 * Tools never take a user id as an argument — an AI client cannot ask to act as someone else.
 */
@Configuration
class McpServerConfig {

	static final String USER_ID = "minibank.userId";

	@Bean
	WebMvcStatelessServerTransport webMvcStatelessServerTransport(JsonMapper jsonMapper,
			McpServerStreamableHttpProperties properties) {
		return WebMvcStatelessServerTransport.builder()
			.jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
			.messageEndpoint(properties.getMcpEndpoint())
			.contextExtractor(McpServerConfig::authenticatedUser)
			.build();
	}

	/** Runs on the servlet request thread, after Spring Security has already verified the bearer token. */
	static McpTransportContext authenticatedUser(ServerRequest request) {
		Principal principal = request.servletRequest().getUserPrincipal();
		if (principal instanceof JwtAuthenticationToken token) {
			return McpTransportContext.create(Map.of(USER_ID, UUID.fromString(token.getToken().getSubject())));
		}
		return McpTransportContext.EMPTY;
	}

}
