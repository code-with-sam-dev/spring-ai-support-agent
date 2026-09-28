#!/usr/bin/env python3
"""Mint a demo ticket token, HS256, standard library only.

    scripts/token.py ticket CUST-17        # a support ticket for one customer
    scripts/token.py lead                  # a support lead who may approve refunds

The secret comes from SUPPORT_JWT_SECRET, generated fresh by scripts/verify.sh.
Demo identity only: a real deployment gets tokens from its identity provider."""
import base64, hashlib, hmac, json, os, sys, time

def b64(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()

kind = sys.argv[1]
now = int(time.time())
claims = {"iat": now, "exp": now + 3600}
if kind == "ticket":
    claims |= {"sub": "agent-ana", "customer_id": sys.argv[2], "scope": "ticket"}
elif kind == "lead":
    claims |= {"sub": "lead-ben", "scope": "refunds:approve"}
else:
    raise SystemExit("usage: token.py ticket <customer> | lead")
head = b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
body = b64(json.dumps(claims).encode())
secret = os.environ.get("SUPPORT_JWT_SECRET")
if not secret:  # fall back to the git-ignored local file verify.sh writes
    env = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env.local")
    secret = dict(l.strip().split("=", 1) for l in open(env) if "=" in l)["SUPPORT_JWT_SECRET"]
sig = hmac.new(secret.encode(), f"{head}.{body}".encode(), hashlib.sha256).digest()
print(f"{head}.{body}.{b64(sig)}")
