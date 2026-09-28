package dev.example.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Every boundary, proved with no model at all.
 *
 * These tests speak MCP over HTTP to the real server, exactly as Claude does,
 * against a real Postgres with pgvector. They cost nothing and give the same
 * answer every run, which is why the boundaries live in the server: a model's
 * behaviour can only be sampled, the server's can be tested.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "support.jwt.secret=" + BoundaryTests.SECRET)
class BoundaryTests {

    static final String SECRET = "test-only-secret-0123456789abcdef0123456789abcdef";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @LocalServerPort int port;
    @Autowired JdbcClient db;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clean() {
        db.sql("DELETE FROM provider_calls; DELETE FROM refund_audit; DELETE FROM refunds").update();
    }

    @Test
    void noToolTakesACustomerId() throws Exception {
        var tools = mcp(ticket("CUST-17"), "tools/list", null);
        assertThat(tools).contains("recent_payments", "request_refund").doesNotContainIgnoringCase("customerId\"");
    }

    @Test
    void anotherCustomersPaymentIsInvisible() throws Exception {
        var result = call(ticket("CUST-17"), "payment_detail", "{\"paymentId\":\"PAY-2210\"}");
        assertThat(result).contains("\"isError\":true", "No payment PAY-2210 on this ticket");
    }

    @Test
    void fullCardNumbersNeverLeaveTheServer() throws Exception {
        var result = call(ticket("CUST-17"), "recent_payments", "{}");
        assertThat(result).contains("**** 4242").doesNotContainPattern("\\d{13,19}");
    }

    @Test
    void requestingARefundPaysNothing() throws Exception {
        var result = call(ticket("CUST-17"), "request_refund",
                "{\"paymentId\":\"PAY-1043-B\",\"amountCents\":4900,\"reason\":\"duplicate\"}");
        assertThat(result).contains("PENDING");
        assertThat(count("provider_calls")).isZero();
    }

    @Test
    void aTicketCannotApprove() throws Exception {
        var id = requestDuplicateRefund();
        assertThat(approve(ticket("CUST-17"), id)).isEqualTo(403);
        assertThat(count("provider_calls")).isZero();
    }

    @Test
    void approvingTwicePaysOnce() throws Exception {
        var id = requestDuplicateRefund();
        assertThat(approve(lead(), id)).isEqualTo(200);
        assertThat(approve(lead(), id)).isEqualTo(200);
        assertThat(count("provider_calls")).isEqualTo(1);
        assertThat(db.sql("SELECT event FROM refund_audit WHERE refund_id = :id::uuid ORDER BY id")
                .param("id", id).query(String.class).list())
                .containsExactly("REQUESTED", "APPROVAL_RECEIVED", "EXECUTED", "APPROVAL_RECEIVED", "APPROVAL_IGNORED");
    }

    @Test
    void aForgedTokenIsRefused() throws Exception {
        var forged = jwt("{\"sub\":\"x\",\"customer_id\":\"CUST-42\",\"scope\":\"ticket\",\"exp\":"
                + (Instant.now().getEpochSecond() + 600) + "}", "not-the-server-secret-not-the-server-secret");
        assertThat(post(forged, "/mcp", initialize(), null).statusCode()).isEqualTo(401);
    }

    @Test
    void policySearchNamesItsSource() throws Exception {
        var result = call(ticket("CUST-17"), "search_policy", "{\"question\":\"Do you refund processing fees?\"}");
        assertThat(result).contains("refunds#fees");
    }

    // ---- a small MCP client: initialize, then one request, as Claude Code does ----

    String requestDuplicateRefund() throws Exception {
        var r = call(ticket("CUST-17"), "request_refund",
                "{\"paymentId\":\"PAY-1043-B\",\"amountCents\":4900,\"reason\":\"duplicate\"}");
        var m = java.util.regex.Pattern.compile("\\\\\"id\\\\\":\\\\\"([0-9a-f-]{36})").matcher(r);
        assertThat(m.find()).as(r).isTrue();
        return m.group(1);
    }

    String call(String token, String tool, String args) throws Exception {
        return mcp(token, "tools/call", "{\"name\":\"" + tool + "\",\"arguments\":" + args + "}");
    }

    String mcp(String token, String method, String params) throws Exception {
        var init = post(token, "/mcp", initialize(), null);
        var session = init.headers().firstValue("Mcp-Session-Id").orElseThrow();
        post(token, "/mcp", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", session);
        var body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"" + method + "\""
                + (params == null ? "" : ",\"params\":" + params) + "}";
        return post(token, "/mcp", body, session).body();
    }

    static String initialize() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\","
                + "\"capabilities\":{},\"clientInfo\":{\"name\":\"tests\",\"version\":\"1\"}}}";
    }

    HttpResponse<String> post(String token, String path, String body, String session) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer " + token);
        if (session != null) req.header("Mcp-Session-Id", session);
        return http.send(req.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    int approve(String token, String refundId) throws Exception {
        return post(token, "/admin/refunds/" + refundId + "/approve", "", null).statusCode();
    }

    int count(String table) {
        return db.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    static String ticket(String customer) throws Exception {
        return jwt("{\"sub\":\"agent-ana\",\"customer_id\":\"" + customer + "\",\"scope\":\"ticket\",\"exp\":"
                + (Instant.now().getEpochSecond() + 600) + "}", SECRET);
    }

    static String lead() throws Exception {
        return jwt("{\"sub\":\"lead-ben\",\"scope\":\"refunds:approve\",\"exp\":"
                + (Instant.now().getEpochSecond() + 600) + "}", SECRET);
    }

    static String jwt(String claims, String secret) throws Exception {
        var enc = Base64.getUrlEncoder().withoutPadding();
        var head = enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        var body = enc.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return head + "." + body + "." + enc.encodeToString(mac.doFinal((head + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
}
