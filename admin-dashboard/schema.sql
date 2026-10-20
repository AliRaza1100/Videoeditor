PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;
CREATE TABLE IF NOT EXISTS admins(email TEXT PRIMARY KEY, salt TEXT NOT NULL, password_hash TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY, email TEXT NOT NULL REFERENCES admins(email), expires INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS devices(
 id TEXT PRIMARY KEY, credential_hash TEXT NOT NULL, analytics INTEGER NOT NULL DEFAULT 0,
 brand TEXT NOT NULL DEFAULT '', model TEXT NOT NULL DEFAULT '', api_level INTEGER,
 density INTEGER, locale TEXT NOT NULL DEFAULT '', country TEXT NOT NULL DEFAULT '', app_version TEXT NOT NULL DEFAULT '',
 first_seen TEXT NOT NULL, last_seen TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS devices_active ON devices(analytics,last_seen);
CREATE TABLE IF NOT EXISTS ad_config_receipts(
 device_id TEXT PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
 version INTEGER NOT NULL, sdk_enabled INTEGER NOT NULL,
 app_version TEXT NOT NULL, received_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS events(
 device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE, event_id TEXT NOT NULL,
 type TEXT NOT NULL, feature TEXT NOT NULL DEFAULT '', occurred_at TEXT NOT NULL, received_at TEXT NOT NULL,
 PRIMARY KEY(device_id,event_id)
);
CREATE INDEX IF NOT EXISTS events_retention ON events(received_at);
CREATE TABLE IF NOT EXISTS usage_daily(
 device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE, day TEXT NOT NULL,
 type TEXT NOT NULL, feature TEXT NOT NULL DEFAULT '', count INTEGER NOT NULL CHECK(count>=0),
 PRIMARY KEY(device_id,day,type,feature)
);
CREATE INDEX IF NOT EXISTS usage_day ON usage_daily(day,type);
CREATE TABLE IF NOT EXISTS tickets(
 id TEXT PRIMARY KEY, device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
 subject TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('open','pending','closed')),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS tickets_updated ON tickets(updated_at);
CREATE TABLE IF NOT EXISTS messages(
 id TEXT PRIMARY KEY, ticket_id TEXT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
 author TEXT NOT NULL CHECK(author IN ('user','admin')), body TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS config_versions(
 version INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL, actor TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS config_draft(id INTEGER PRIMARY KEY CHECK(id=1),revision INTEGER NOT NULL,body TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY,actor TEXT NOT NULL,action TEXT NOT NULL,detail TEXT NOT NULL,created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS revenue_reports(
 day TEXT NOT NULL,currency TEXT NOT NULL,estimated_micros INTEGER NOT NULL CHECK(estimated_micros>=0),
 impressions INTEGER NOT NULL CHECK(impressions>=0),source TEXT NOT NULL,updated_at TEXT NOT NULL,
 PRIMARY KEY(day,currency)
);
