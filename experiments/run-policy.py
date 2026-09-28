#!/usr/bin/env python3
"""The SAY experiment: 24 fixed policy questions, with and without the policy tool.

Same model, same prompt, same questions. Condition "with" allows search_policy;
condition "without" does not. For every answer it records:
  correct    the answer contains what the policy supports (the question's must pattern)
  unsupported  the answer states something the policy does not (its wrong pattern)
  cited      the source ids the answer names
  verified   every cited id was actually returned by search_policy in that run
Pattern scoring is rough, so every answer is kept for reading, and any case where a
reading disagrees with the pattern is listed in the results, not quietly changed.

    python3 experiments/run-policy.py            # both conditions, all questions
"""
import json, os, re, subprocess, sys
from pathlib import Path

DIR = Path(__file__).resolve().parent.parent
OUT = DIR / "runs" / "policy"; OUT.mkdir(parents=True, exist_ok=True)
READ_TOOLS = "mcp__payments__recent_payments mcp__payments__payment_detail"
# (allowed, hidden). A hidden tool is removed from what the model sees, so "without"
# means no retrieval at all, not a retrieval tool the model tries and is refused.
CONDITIONS = {
    "with": (READ_TOOLS + " mcp__payments__search_policy", "mcp__payments__request_refund"),
    "without": (READ_TOOLS, "mcp__payments__search_policy mcp__payments__request_refund"),
}

rows = [l.rstrip("\n").split("\t") for l in (DIR / "experiments/policy-questions.tsv").read_text().splitlines()
        if l and not l.startswith("#")]
results = []
for cond, (allowed, hidden) in CONDITIONS.items():
    for qid, kind, question, must, wrong, sources in rows:
        stream = OUT / f"{cond}-{qid}.jsonl"
        if not stream.exists():
            subprocess.run([str(DIR / "scripts/ask.sh"), "CUST-17", question, str(stream)],
                           env=dict(os.environ, ALLOWED=allowed, DISALLOWED=hidden), stdout=subprocess.DEVNULL, check=True)
        answer, returned = "", set()
        for line in stream.read_text().splitlines():
            try:
                e = json.loads(line)
            except json.JSONDecodeError:
                continue
            if e.get("type") == "result":
                answer = e.get("result", "")
            for b in (e.get("message", {}) or {}).get("content", []) if isinstance(e.get("message"), dict) else []:
                if isinstance(b, dict) and b.get("type") == "tool_result":
                    returned |= set(re.findall(r"refunds#[a-z]+", json.dumps(b.get("content"))))
        cited = set(re.findall(r"refunds#[a-z]+", answer))
        results.append({
            "condition": cond, "id": qid, "kind": kind,
            "correct": bool(re.search(must, answer, re.I)),
            "unsupported": bool(re.search(wrong, answer, re.I)),
            "cited": sorted(cited), "verified": cited <= returned if cited else None,
            "answer": answer})
        print(cond, qid, kind, results[-1]["correct"], results[-1]["unsupported"], sorted(cited), flush=True)

(OUT / "results.json").write_text(json.dumps(results, indent=1))
for cond in CONDITIONS:
    r = [x for x in results if x["condition"] == cond]
    print(f"{cond}: correct {sum(x['correct'] for x in r)} of {len(r)}, "
          f"unsupported claims {sum(x['unsupported'] for x in r)} of {len(r)}, "
          f"answers citing a source {sum(bool(x['cited']) for x in r)}, "
          f"citations all verified {sum(x['verified'] is True for x in r)}")
