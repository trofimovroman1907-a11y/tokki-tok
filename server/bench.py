"""Mods offered for TikTok Mod Bench (margy.narez.xyz/bench): kept here, and the owner told in Telegram.

POST /bench {name, platform, source, telegram, contact, about}. A form anybody can send, so: short fields, three
offers an hour from one address, fifty a day in all, and nothing from it is ever shown on the site by itself.
"""
import html
import time

use = {}

FIELDS = {'name': 60, 'platform': 20, 'source': 300, 'telegram': 100, 'contact': 100, 'about': 1500}


def prepare(db):
    db.executescript("""
        CREATE TABLE IF NOT EXISTS bench_offer (
            id       INTEGER PRIMARY KEY AUTOINCREMENT,
            at       INTEGER NOT NULL,
            ip       TEXT NOT NULL DEFAULT '',
            name     TEXT NOT NULL DEFAULT '',
            platform TEXT NOT NULL DEFAULT '',
            source   TEXT NOT NULL DEFAULT '',
            telegram TEXT NOT NULL DEFAULT '',
            contact  TEXT NOT NULL DEFAULT '',
            about    TEXT NOT NULL DEFAULT ''
        );
        CREATE INDEX IF NOT EXISTS bench_offer_at ON bench_offer (at);
    """)
    db.commit()


def offer(body, ip):
    got = {k: ' '.join(str(body.get(k) or '').split())[:n] if k != 'about' else str(body.get(k) or '').strip()[:n]
           for k, n in FIELDS.items()}
    if not got['name'] or not got['source'] or not got['contact']:
        return 400, {'error': 'empty'}
    now = int(time.time())
    db = use['connect']()
    try:
        if db.execute('SELECT COUNT(*) FROM bench_offer WHERE ip = ? AND at > ?', (ip, now - 3600)).fetchone()[0] >= 3 \
                or db.execute('SELECT COUNT(*) FROM bench_offer WHERE at > ?', (now - 86400,)).fetchone()[0] >= 50:
            return 429, {'error': 'too often'}
        db.execute('INSERT INTO bench_offer (at, ip, name, platform, source, telegram, contact, about) VALUES (?,?,?,?,?,?,?,?)',
                   (now, ip, got['name'], got['platform'], got['source'], got['telegram'], got['contact'], got['about']))
        db.commit()
    finally:
        db.close()
    e = html.escape
    use['tell']('Новый мод для бенча: <b>%s</b> (%s)\nКод: %s\nКанал: %s\nСвязь: %s\n\n%s' % (
        e(got['name']), e(got['platform'] or '?'), e(got['source']), e(got['telegram'] or '—'), e(got['contact']), e(got['about'][:800])))
    return 200, {'ok': True}
