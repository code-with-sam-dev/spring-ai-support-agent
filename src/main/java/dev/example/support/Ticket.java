package dev.example.support;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The support ticket this request belongs to, read from the signed token.
 *
 * The customer comes from here and nowhere else. No tool takes a customer id as a
 * parameter, so the model cannot ask for another customer's data: it has no field
 * to put one in.
 */
public record Ticket(String customerId, String agent) {

    public static Ticket current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("no authenticated ticket");
        }
        return new Ticket(jwt.getClaimAsString("customer_id"), jwt.getSubject());
    }
}
