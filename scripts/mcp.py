#!/usr/bin/env python3
"""Call one MCP tool over streamable HTTP, with no model involved.

    scripts/mcp.py <token> tools/list
    scripts/mcp.py <token> <tool> '<json arguments>'

Prints the tool result exactly as the server sent it: what a model would read."""
import json, os, sys, urllib.request

URL = os.environ.get("MCP_URL", "http://localhost:8420/mcp")
token, tool = sys.argv[1], sys.argv[2]
args = json.loads(sys.argv[3]) if len(sys.argv) > 3 else {}

def post(body, session=None):
    headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream",
               "Authorization": f"Bearer {token}"}
    if session:
        headers["Mcp-Session-Id"] = session
    req = urllib.request.Request(URL, json.dumps(body).encode(), headers)
    with urllib.request.urlopen(req) as r:
        text = r.read().decode()
        # Server-sent events: "data:" with or without a space after the colon.
        data = [l[5:].lstrip() for l in text.splitlines() if l.startswith("data:")]
        return r.headers.get("Mcp-Session-Id"), json.loads(data[-1] if data else text) if text.strip() else None

session, _ = post({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
    "protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "mcp.py", "version": "1"}}})
post({"jsonrpc": "2.0", "method": "notifications/initialized"}, session)
if tool == "tools/list":
    _, out = post({"jsonrpc": "2.0", "id": 2, "method": "tools/list"}, session)
else:
    _, out = post({"jsonrpc": "2.0", "id": 2, "method": "tools/call",
                   "params": {"name": tool, "arguments": args}}, session)
print(json.dumps(out["result"], indent=1))
