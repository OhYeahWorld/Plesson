"""Конфигурация сервера PLesson (Тема 4: конфигурационный файл .env)."""
import os


def _load_env(path):
    """Минимальный загрузчик .env без внешних зависимостей."""
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    os.environ.setdefault(k.strip(), v.strip())


_load_env(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env"))

# --- Подключение к PostgreSQL -------------------------------------------------
PG_HOST = os.getenv("PG_HOST", "localhost")
PG_PORT = int(os.getenv("PG_PORT", "5432"))
PG_DB = os.getenv("PG_DB", "plesson")
PG_USER = os.getenv("PG_USER", "postgres")
PG_PASSWORD = os.getenv("PG_PASSWORD", "")

DSN = {
    "host": PG_HOST,
    "port": PG_PORT,
    "dbname": PG_DB,
    "user": PG_USER,
    "password": PG_PASSWORD,
}

# --- Внешний источник данных (pleh.tech / PostgREST) --------------------------
PLEH_BASE = os.getenv("PLEH_BASE", "https://pleh.tech/rest/v1")
PLEH_TOKEN = os.getenv(
    "PLEH_TOKEN",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
    "eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIiwiaWF0IjoxNzgxNDM2NjY5LCJleHAiOjIwOTY3OTY2Njl9."
    "uVBH4rCUVsarTmNqhiKBw8kvLqKygVvs31fkWW0C6u0",
)

API_TITLE = "PLesson API"
API_VERSION = "1.0.0"
