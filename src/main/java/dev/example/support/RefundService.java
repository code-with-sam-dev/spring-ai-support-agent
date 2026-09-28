package dev.example.support;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that can move money, and it never takes the model's word for anything.
 *
 * A refund is requested, then approved by a person, then executed exactly once:
 * PENDING, then EXECUTING, then EXECUTED. Each step is a conditional update, so a
 * second approval of the same refund finds nothing to update and pays nothing.
 */
@Service
public class RefundService {

    public record Refund(UUID id, String paymentId, String customerId, long amountCents, String status) {}

    private final JdbcClient db;
    private final CardNetwork network;

    public RefundService(JdbcClient db, CardNetwork network) {
        this.db = db;
        this.network = network;
    }

    /** What the model can do: ask. The refund waits for a person. */
    @Transactional
    public Refund request(Ticket ticket, String paymentId, long amountCents, String reason) {
        var payment = db.sql("SELECT amount_cents FROM payments WHERE id = :id AND customer_id = :customer")
                .param("id", paymentId).param("customer", ticket.customerId())
                .query(Long.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("No payment " + paymentId + " on this ticket"));
        if (amountCents <= 0 || amountCents > payment) {
            throw new IllegalArgumentException("Refund must be between 1 and " + payment + " cents");
        }
        var id = UUID.randomUUID();
        try {
            db.sql("""
                    INSERT INTO refunds (id, payment_id, customer_id, amount_cents, reason, status, requested_by, requested_at)
                    VALUES (:id, :payment, :customer, :amount, :reason, 'PENDING', :agent, :now)""")
                    .param("id", id).param("payment", paymentId).param("customer", ticket.customerId())
                    .param("amount", amountCents).param("reason", reason).param("agent", ticket.agent())
                    .param("now", java.sql.Timestamp.from(Instant.now()))
                    .update();
        } catch (DuplicateKeyException alreadyOpen) {
            // One open refund per payment: asking again returns the one already waiting.
            return db.sql("SELECT * FROM refunds WHERE payment_id = :p AND status IN ('PENDING', 'EXECUTING')")
                    .param("p", paymentId).query(RefundService::refund).single();
        }
        audit(id, "REQUESTED", ticket.agent(), reason);
        return new Refund(id, paymentId, ticket.customerId(), amountCents, "PENDING");
    }

    /** What a support lead can do: approve. Delivered twice, it still pays once. */
    public String approve(UUID refundId, String lead) {
        audit(refundId, "APPROVAL_RECEIVED", lead, null);
        var claimed = db.sql("""
                UPDATE refunds SET status = 'EXECUTING', approved_by = :lead
                WHERE id = :id AND status = 'PENDING'
                  AND customer_id = (SELECT customer_id FROM payments WHERE payments.id = refunds.payment_id)""")
                .param("id", refundId).param("lead", lead)
                .update();
        if (claimed == 0) {
            audit(refundId, "APPROVAL_IGNORED", lead, "not pending, or the payment is not this customer's");
            return "not executed";
        }
        network.refund(refundId);
        db.sql("UPDATE refunds SET status = 'EXECUTED', executed_at = now() WHERE id = :id")
                .param("id", refundId).update();
        audit(refundId, "EXECUTED", lead, null);
        return "executed";
    }

    private void audit(UUID refundId, String event, String actor, String detail) {
        db.sql("INSERT INTO refund_audit (refund_id, event, actor, detail) VALUES (:r, :e, :a, :d)")
                .param("r", refundId).param("e", event).param("a", actor).param("d", detail)
                .update();
    }

    static Refund refund(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Refund(rs.getObject("id", UUID.class), rs.getString("payment_id"), rs.getString("customer_id"),
                rs.getLong("amount_cents"), rs.getString("status"));
    }
}
