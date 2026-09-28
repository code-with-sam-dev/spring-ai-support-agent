package dev.example.support;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The model's only way near a refund. The name says the authority it has:
 * requestRefund, not issueRefund. Nothing here moves money.
 */
@Component
public class RefundTools {

    private final RefundService refunds;

    public RefundTools(RefundService refunds) {
        this.refunds = refunds;
    }

    @McpTool(name = "request_refund",
            description = "Ask for a refund on one of this customer's payments. A person approves it; nothing is paid by this call.")
    public RefundService.Refund requestRefund(
            @McpToolParam(description = "Payment id, e.g. PAY-1043-B") String paymentId,
            @McpToolParam(description = "Amount in cents") long amountCents,
            @McpToolParam(description = "Why, in the customer's words") String reason) {
        return refunds.request(Ticket.current(), paymentId, amountCents, reason);
    }
}
