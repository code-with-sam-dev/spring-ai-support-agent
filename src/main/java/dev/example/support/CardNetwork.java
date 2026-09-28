package dev.example.support;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Stands in for the card network. Every row it writes is money that moved. */
@Component
public class CardNetwork {

    private final JdbcClient db;

    public CardNetwork(JdbcClient db) {
        this.db = db;
    }

    public void refund(UUID refundId) {
        db.sql("INSERT INTO provider_calls (refund_id) VALUES (:r)")
                .param("r", refundId)
                .update();
    }
}
