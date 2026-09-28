package dev.example.support;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every call carries a signed ticket token.
 *
 * The MCP endpoint needs a ticket scoped to one customer. Approving a refund needs
 * a different scope, held by a support lead, and is not an MCP tool at all, so the
 * model has no way to call it.
 *
 * DEMO KEY, NOT PRODUCTION IDENTITY. The HMAC secret comes from the environment
 * and scripts/verify.sh generates a fresh one per run. A real deployment validates
 * tokens from its identity provider; MCP's own OAuth support in Spring AI is still
 * marked experimental, which is why it is named here and not used.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Errors keep their real status: a missing endpoint
                        // answers 404, not 403.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/mcp/**")
                                .hasAuthority("SCOPE_ticket")
                        .requestMatchers("/admin/**")
                                .hasAuthority("SCOPE_refunds:approve")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${support.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
