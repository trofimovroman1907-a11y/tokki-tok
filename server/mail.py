"""Letters to the mod's mailbox (MargyT 2.3.1).

The owner writes them from the admin in the mod (admin_mod, ops mail_*): to everybody, or to some accounts by
their TikTok ids, maybe only to one version of the mod, maybe only for a while. The mod asks POST /mail with its
account id and version and gets what is meant for it.

A letter to an account is given to whoever says that account's id, and ids are public: nothing secret in them.
"""
import time

use = {}

MOST = 40
TITLE_MOST = 120
BODY_MOST = 4000


def prepare(db):
    db.executescript("""
        CREATE TABLE IF NOT EXISTS mail (
            id      INTEGER PRIMARY KEY AUTOINCREMENT,
            to_uid  TEXT NOT NULL DEFAULT '',
            version TEXT NOT NULL DEFAULT '',
            title   TEXT NOT NULL DEFAULT '',
            body    TEXT NOT NULL DEFAULT '',
            sender  TEXT NOT NULL DEFAULT '',
            sent    INTEGER NOT NULL DEFAULT 0,
            until   INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS mail_to ON mail (to_uid);
    """)
    db.commit()


def _letter(row):
    return {"id": row[0], "title": row[1], "body": row[2], "from": row[3], "sent": row[4]}


def inbox(body):
    """What one phone should have: letters to everybody and to its account, newest first."""
    uid = str(body.get("uid") or "")
    if uid and not use["sane"](uid):
        uid = ""
    version = str(body.get("version") or "")[:40]
    now = int(time.time())
    db = use["connect"]()
    try:
        rows = db.execute(
            "SELECT id, title, body, sender, sent, version FROM mail"
            " WHERE (to_uid = '' OR to_uid = ?) AND (until = 0 OR until > ?) AND sent <= ?"
            " ORDER BY sent DESC, id DESC LIMIT ?", (uid or "-", now, now, MOST * 2)).fetchall()
        out = [_letter(r) for r in rows if not r[5] or version.startswith(r[5])][:MOST]
        return 200, {"mail": out}
    finally:
        db.close()


def admin(db, op, said):
    """mail_send, mail_list, mail_drop: from admin_mod.act, already signed and approved."""
    if op == "mail_send":
        title = " ".join(str(said.get("title") or "").split())[:TITLE_MOST]
        text = str(said.get("body") or "").strip()[:BODY_MOST]
        sender = " ".join(str(said.get("from") or "").split())[:60]
        version = str(said.get("version") or "").strip()[:40]
        if not text:
            return 400, {"error": "empty"}
        to = [w for w in str(said.get("to") or "").replace(",", " ").split() if w]
        if any(not use["sane"](w) for w in to):
            return 400, {"error": "target"}
        try:
            days = max(0, min(365, int(said.get("days") or 0)))
        except (TypeError, ValueError):
            days = 0
        now = int(time.time())
        until = now + days * 86400 if days else 0
        for who in (to or [""]):
            db.execute("INSERT INTO mail (to_uid, version, title, body, sender, sent, until) VALUES (?,?,?,?,?,?,?)",
                       (who, version, title, text, sender, now, until))
        db.commit()
        return 200, sent(db)
    if op == "mail_list":
        return 200, sent(db)
    if op == "mail_drop":
        try:
            which = int(said.get("id"))
        except (TypeError, ValueError):
            return 400, {"error": "id"}
        db.execute("DELETE FROM mail WHERE id = ?", (which,))
        db.commit()
        return 200, sent(db)
    return 400, {"error": "op"}


def sent(db):
    rows = db.execute("SELECT id, title, body, sender, sent, to_uid, version, until FROM mail"
                      " ORDER BY id DESC LIMIT 60").fetchall()
    return {"mail": [dict(_letter(r), to=r[5], version=r[6], until=r[7]) for r in rows]}
