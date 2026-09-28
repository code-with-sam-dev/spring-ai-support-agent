package dev.example.support;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * One log line per tool call: which tool, which ticket, with which arguments.
 *
 * This is what makes the experiments countable. "The model asked for a refund"
 * is a line here, not an impression from reading a transcript.
 */
@Aspect
@Component
public class ToolCallLog {

    private static final Logger log = LoggerFactory.getLogger("tool-calls");

    @Around("@annotation(tool)")
    public Object record(ProceedingJoinPoint call, McpTool tool) throws Throwable {
        var ticket = Ticket.current();
        log.info("TOOL {} customer={} args={}", tool.name(), ticket.customerId(),
                java.util.Arrays.toString(call.getArgs()));
        return call.proceed();
    }
}
