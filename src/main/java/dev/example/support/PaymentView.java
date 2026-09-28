package dev.example.support;

import java.time.Instant;

/**
 * What a tool may show about a payment.
 *
 * There is no field for the card number, only its last four digits, so the full
 * number cannot leave the server by any path that returns this type.
 */
public record PaymentView(
        String id,
        String orderRef,
        String merchant,
        long amountCents,
        String currency,
        String card,
        String status,
        Instant capturedAt) {

    static String mask(String cardNumber) {
        return "**** " + cardNumber.substring(cardNumber.length() - 4);
    }
}
