package dev.example.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PaymentTools {

    private final JdbcClient db;

    public PaymentTools(JdbcClient db) {
        this.db = db;
    }

    @McpTool(name = "recent_payments",
            description = "The recent payments of the customer this ticket is about")
    public List<PaymentView> recentPayments() {
        return db.sql("""
                SELECT * FROM payments WHERE customer_id = :customer
                ORDER BY captured_at DESC LIMIT 20""")
                .param("customer", Ticket.current().customerId())
                .query(PaymentTools::view)
                .list();
    }

    @McpTool(name = "payment_detail",
            description = "One payment of the customer this ticket is about")
    public PaymentView paymentDetail(
            @McpToolParam(description = "Payment id, e.g. PAY-1043-A")
            String paymentId) {
        return db.sql("""
                SELECT * FROM payments
                WHERE id = :id AND customer_id = :customer""")
                .param("id", paymentId)
                .param("customer", Ticket.current().customerId())
                .query(PaymentTools::view)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No payment " + paymentId + " on this ticket"));
    }

    static PaymentView view(ResultSet rs, int row) throws SQLException {
        return new PaymentView(
                rs.getString("id"),
                rs.getString("order_ref"),
                rs.getString("merchant"),
                rs.getLong("amount_cents"),
                rs.getString("currency"),
                PaymentView.mask(rs.getString("card_number")),
                rs.getString("status"),
                rs.getTimestamp("captured_at").toInstant());
    }
}
