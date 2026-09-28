package dev.example.support;

import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class PolicyTools {

    private final PolicyLibrary policy;

    public PolicyTools(PolicyLibrary policy) {
        this.policy = policy;
    }

    @McpTool(name = "search_policy",
            description = "Search the refund policy. Answer policy questions only from these passages, and cite their source ids.")
    public List<PolicyLibrary.Passage> searchPolicy(@McpToolParam(description = "The customer's question") String question) {
        return policy.search(question);
    }
}
