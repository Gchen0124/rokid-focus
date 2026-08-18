#!/usr/bin/env python3
"""Minimal task list for laptop mock + phone. Same Wi-Fi. Last PUT wins."""
from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "tasks.json"
PORT = 8787


def load() -> dict:
    if DATA.exists():
        return json.loads(DATA.read_text(encoding="utf-8"))
    return {"tasks": []}


def save(body: dict) -> dict:
    tasks = body.get("tasks") if isinstance(body, dict) else None
    if not isinstance(tasks, list):
        tasks = []
    clean = []
    for item in tasks:
        if not isinstance(item, dict):
            continue
        title = str(item.get("title", "")).strip()
        if not title:
            continue
        try:
            usd = float(item.get("usd", item.get("value", 0)))
        except (TypeError, ValueError):
            usd = 0.0
        try:
            minutes = int(item.get("minutes", 30))
        except (TypeError, ValueError):
            minutes = 30
        done = bool(item.get("done"))
        archived = bool(item.get("archived"))
        done_at = str(item.get("doneAt") or "").strip()
        if not done and not archived:
            done_at = ""
        assignee = str(item.get("assignee") or "me").strip().lower()
        if assignee not in ("me", "agent"):
            assignee = "me"
        status = str(item.get("agentStatus") or "idle").strip().lower()
        if status not in ("idle", "queued", "doing", "blocked", "ready"):
            status = "idle"
        if assignee == "me":
            status = "idle"
        clean.append(
            {
                "id": str(item.get("id") or ""),
                "title": title,
                "usd": max(0.0, usd),
                "minutes": max(0, minutes),
                "done": done,
                "doneAt": done_at,
                "archived": archived,
                "assignee": assignee,
                "handoff": str(item.get("handoff") or "").strip(),
                "agentStatus": status,
                "outcome": str(item.get("outcome") or "").strip(),
            }
        )
    clean.sort(key=lambda t: (-t["usd"], t["title"]))
    payload = {"tasks": clean}
    DATA.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return payload


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:
        print("[sync]", fmt % args)

    def _cors(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, PUT, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Cache-Control", "no-store")

    def do_OPTIONS(self) -> None:
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self) -> None:
        if self.path in ("/", "/index.html"):
            html = (ROOT / "index.html").read_bytes()
            self.send_response(200)
            self._cors()
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(html)))
            self.end_headers()
            self.wfile.write(html)
            return
        if self.path.split("?")[0] == "/tasks":
            raw = json.dumps(load(), ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self._cors()
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)
            return
        self.send_error(404)

    def do_PUT(self) -> None:
        if self.path.split("?")[0] != "/tasks":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        raw = json.dumps(save(body), ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self._cors()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)


if __name__ == "__main__":
    if not DATA.exists():
        save({"tasks": []})
    else:
        save(load())
    httpd = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Focus sync  http://127.0.0.1:{PORT}/")
    httpd.serve_forever()
