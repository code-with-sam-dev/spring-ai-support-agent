package dev.example.support;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The approval a person gives. It is HTTP, not an MCP tool, so the model cannot
 * reach it.
 */
@RestController
public class ApprovalController {

    private final RefundService refunds;

    public ApprovalController(RefundService refunds) {
        this.refunds = refunds;
    }

    @PostMapping("/admin/refunds/{id}/approve")
    public Map<String, String> approve(@PathVariable UUID id,
                                       @AuthenticationPrincipal Jwt lead) {
        String result = refunds.approve(id, lead.getSubject());
        return Map.of("refund", id.toString(), "result", result);
    }
}
