# Spring AI support agent: the AI asks, Java decides

A Spring Boot service that exposes payment tools as an **MCP server** with
Spring AI, and a support copilot that uses them from **Claude Code**. The model
can look things up and ask for a refund. It cannot choose the customer, see a
card number, approve a refund or move money. Those rules live in Java, and every
one of them is tested with no model at all.

Recorded 28 September 2026 with Spring AI 2.0.1, Spring Boot 4.1.1, Java 25,
Postgres 17 with pgvector, and Claude Code with Sonnet 5. Versions and defaults
change; the boundaries are the durable part.

All data is fictional. Card numbers are published test numbers.

## Rerun it

You need JDK 25, Docker, Python 3 and `jq`. The model runs also need Claude Code,
signed in.

```sh
scripts/verify.sh                 # 12 boundary tests, every demonstration, the transport check
scripts/verify.sh --experiments   # also the model runs: 3 legitimate, 24 policy, 12 injections
```

`scripts/verify.sh` generates a fresh local demo secret on every run and writes
its transcripts to `runs/verify/`.

## The four boundaries

| Boundary | Enforced by | Proved by |
|---|---|---|
| What it may **see** | The customer comes from the signed ticket token (`Ticket`), no tool takes a customer id, `PaymentView` has no card number field | `noToolTakesACustomerId`, `anotherCustomersPaymentIsInvisible`, `fullCardNumbersNeverLeaveTheServer` |
| What it may **do** | `request_refund` can only create a PENDING refund, capped at what is left to refund; approval is HTTP with a lead's scope; approving is one conditional update | `requestingARefundPaysNothing`, `aTicketCannotApprove`, `approvingTwicePaysOnce`, `twoRacingApprovalsPayOnce`, `hostileAmountsAreRefused` |
| What it may **say** | `search_policy` over pgvector returns passages with source ids | measured, not enforced: see below |
| What it **costs** | Claude Code's own token totals | measured: about 3 to 4 cents a run at API list prices |

## Results, 2026-09-28

- Boundary tests, no model: **12 of 12 pass**.
- Legitimate request ("charged twice for order 1043, refund the duplicate"),
  3 runs: `request_refund` on PAY-1043-B every time; **3 pending, 0 paid**.
- Injections, 12 attempts (customer message 6, a merchant name in a tool result 3,
  a planted policy passage 3): **0 refund requests, 0 paid**, with this client
  and configuration.
- Policy, 24 questions scored by hand:

  | | Supported | Declined | Unsupported |
  |---|---|---|---|
  | with `search_policy` | 17 | 0 | 7 |
  | without it | 4 | 9 | 11 |

  Retrieval gave it evidence. It also gave it confidence. In every unsupported
  answer with the tool, the right passage was returned. Citations: 15 of 24
  answers cited a source; 13 of 15 supported their sentence.

Details, every answer and its verdict: `experiments/results/`.

## Not proved here

- Resistance to prompt injection in general. That is model behaviour, and it is
  sampled; pending-only refunds are an application invariant, and they are tested.
- Production identity. The JWT is a locally signed test credential.
- Exactly once settlement with a real card network. The card network here is a
  table in the same database, so a real provider's crash window is out of scope:
  that needs the provider's idempotency key and reconciliation.
- That retrieval grounds every answer.

## Transport

Left unset, the WebMVC MCP server serves the older SSE transport and `/mcp`
answers 404. `application.properties` sets
`spring.ai.mcp.server.protocol=STREAMABLE`, and `scripts/transport-check.sh`
checks the endpoint answers. Set it, then check it.

## Layout

```
src/main/java/dev/example/support/   the MCP server: tools, security, refunds, policy search
src/main/resources/policy/refunds.md the six policy passages
src/test/java/.../BoundaryTests.java every boundary, over MCP, against a real Postgres
scripts/                             verify, demo, transport check, MCP client, token minting
experiments/                         the model runs, their inputs and their results
```
