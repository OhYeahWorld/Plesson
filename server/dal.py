"""Data Access Layer (Тема 3.1): чистые параметризованные SQL-запросы к PostgreSQL.

Все запросы используют плейсхолдеры %s — SQL-инъекции исключены на уровне DAL.
Операции изменения нескольких таблиц выполняются в транзакциях
(context manager `transaction`).
"""
import psycopg2
import psycopg2.extras
from contextlib import contextmanager

from config import DSN


def get_conn():
    return psycopg2.connect(**DSN)


@contextmanager
def transaction():
    """Единый обработчик транзакций: BEGIN ... COMMIT / ROLLBACK."""
    conn = get_conn()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def dict_cursor(conn):
    return conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)


# ------------------------------- ЧТЕНИЕ --------------------------------------

def list_groups(q: str = "", limit: int = 50, offset: int = 0):
    """GET /api/groups — список групп с поиском (частичное совпадение без учёта регистра)."""
    sql = """
        SELECT group_id, guid::text AS group_guid, name, faculty, program_type, course
        FROM "group"
        WHERE (%(q)s = '' OR LOWER(name) LIKE '%%' || LOWER(%(q)s) || '%%')
        ORDER BY name ASC
        LIMIT %(limit)s OFFSET %(offset)s
    """
    with get_conn() as conn, dict_cursor(conn) as cur:
        cur.execute(sql, {"q": q, "limit": limit, "offset": offset})
        return cur.fetchall()


def count_groups(q: str = "") -> int:
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute(
            'SELECT COUNT(*) FROM "group" '
            "WHERE (%(q)s = '' OR LOWER(name) LIKE '%%' || LOWER(%(q)s) || '%%')",
            {"q": q},
        )
        return cur.fetchone()[0]


def get_group_by_guid(guid: str):
    with get_conn() as conn, dict_cursor(conn) as cur:
        cur.execute(
            'SELECT group_id, guid::text AS group_guid, name, faculty, program_type, course '
            'FROM "group" WHERE guid = %(guid)s::uuid',
            {"guid": guid},
        )
        return cur.fetchone()


def week_schedule(guid: str, date_from: str, date_to: str):
    """Расписание группы за диапазон дат — через хранимую процедуру group_week()."""
    with get_conn() as conn, dict_cursor(conn) as cur:
        cur.execute(
            "SELECT * FROM group_week(%(guid)s::uuid, %(frm)s::date, %(to)s::date)",
            {"guid": guid, "frm": date_from, "to": date_to},
        )
        rows = cur.fetchall()
    result = []
    for r in rows:
        d = dict(r)
        teachers = [t.strip() for t in (d.pop("teacher_names") or "").split(";") if t.strip()]
        d["day"] = d["day"].isoformat()
        d["teacher_names"] = teachers
        result.append(d)
    return result


def teacher_lessons(name: str, date_from: str, date_to: str):
    """Занятия преподавателя за период — для блока «дополнительная информация»."""
    sql = """
        SELECT l.day, l.period, l.discipline, l.workload_type,
               g.name AS group_name, l.room, l.building, l.campus
        FROM lesson l
        JOIN "group" g ON g.group_id = l.group_id
        WHERE l.day BETWEEN %(frm)s::date AND %(to)s::date
          AND (';' || l.teacher_names || ';' LIKE '%%;' || %(name)s || ';%%'
               OR l.teacher_names LIKE '%%' || %(name)s || '%%')
        ORDER BY l.day, l.period
        LIMIT 200
    """
    with get_conn() as conn, dict_cursor(conn) as cur:
        cur.execute(sql, {"name": name, "frm": date_from, "to": date_to})
        rows = cur.fetchall()
    out = []
    for r in rows:
        d = dict(r)
        d["day"] = d["day"].isoformat()
        out.append(d)
    return out


def find_teacher(name: str):
    """Информация о преподавателе по ФИО: кафедры из справочника."""
    with get_conn() as conn, dict_cursor(conn) as cur:
        cur.execute(
            """
            SELECT t.slug, t.full_name,
                   array_agg(DISTINCT dp.name) AS departments
            FROM teacher t
            LEFT JOIN teacher_department td ON td.teacher_id = t.teacher_id
            LEFT JOIN department dp ON dp.department_id = td.department_id
            WHERE t.full_name = %(name)s
               OR %(name)s = ANY(string_to_array(t.full_name, ' '))
            GROUP BY t.slug, t.full_name
            LIMIT 5
            """,
            {"name": name},
        )
        return cur.fetchall()


# ------------------------------ ИЗМЕНЕНИЕ ------------------------------------

def upsert_group(guid: str, name: str, faculty, program_type, course):
    """CRUD-C/U: создание или обновление группы (демонстрация транзакции)."""
    sql = """
        INSERT INTO "group" (guid, name, faculty, program_type, course)
        VALUES (%(guid)s::uuid, %(name)s, %(faculty)s, %(program_type)s, %(course)s)
        ON CONFLICT (guid) DO UPDATE
        SET name = EXCLUDED.name, faculty = EXCLUDED.faculty,
            program_type = EXCLUDED.program_type, course = EXCLUDED.course
    """
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(sql, {
            "guid": guid, "name": name, "faculty": faculty,
            "program_type": program_type, "course": course,
        })


def upsert_teachers_departments(teachers):
    """Одна транзакция на пакет: учителя + кафедры + связи M:M."""
    with transaction() as conn, conn.cursor() as cur:
        for t in teachers:
            slug = t.get("teacher_slug") or ""
            full = (t.get("teacher_name") or "").strip()
            if not slug or not full:
                continue
            parts = full.split()
            last = parts[0] if parts else ""
            first = parts[1] if len(parts) > 1 else ""
            middle = parts[2] if len(parts) > 2 else ""
            cur.execute(
                """INSERT INTO teacher (slug, last_name, first_name, middle_name)
                   VALUES (%s,%s,%s,%s)
                   ON CONFLICT (slug) DO UPDATE
                   SET last_name=EXCLUDED.last_name, first_name=EXCLUDED.first_name,
                       middle_name=EXCLUDED.middle_name""",
                (slug, last, first, middle),
            )
            tid = None
            cur.execute("SELECT teacher_id FROM teacher WHERE slug=%s", (slug,))
            row = cur.fetchone()
            tid = row[0] if row else None
            for dep in t.get("departments") or []:
                if not dep:
                    continue
                cur.execute(
                    "INSERT INTO department (name) VALUES (%s) ON CONFLICT (name) DO NOTHING",
                    (dep,),
                )
                cur.execute("SELECT department_id FROM department WHERE name=%s", (dep,))
                did = cur.fetchone()[0]
                cur.execute(
                    "INSERT INTO teacher_department (teacher_id, department_id) VALUES (%s,%s) "
                    "ON CONFLICT DO NOTHING",
                    (tid, did),
                )


def upsert_lessons(group_pk: int, lessons):
    """Пакетное сохранение занятий одной транзакцией (BEGIN...COMMIT внутри)."""
    sql = """
        INSERT INTO lesson (group_id, day, period, discipline, workload_type, room,
                            building, campus, subgroup, approved, teacher_names, source_key)
        VALUES (%(gid)s, %(day)s::date, %(period)s, %(discipline)s, %(workload)s, %(room)s,
                %(building)s, %(campus)s, %(subgroup)s, %(approved)s, %(teachers)s, %(key)s)
        ON CONFLICT (source_key) DO UPDATE
        SET discipline = EXCLUDED.discipline,
            workload_type = EXCLUDED.workload_type,
            room = EXCLUDED.room,
            building = EXCLUDED.building,
            campus = EXCLUDED.campus,
            subgroup = EXCLUDED.subgroup,
            approved = EXCLUDED.approved,
            teacher_names = EXCLUDED.teacher_names,
            synced_at = now()
    """
    with transaction() as conn, conn.cursor() as cur:
        for l in lessons:
            teachers = l.get("teacher_names") or (
                [l.get("teacher_name")] if l.get("teacher_name") else []
            )
            key = "|".join([
                str(group_pk), str(l.get("day")), str(l.get("period")),
                str(l.get("discipline")), str(l.get("subgroup")),
                ";".join(t for t in teachers if t),
            ])
            cur.execute(sql, {
                "gid": group_pk,
                "day": l.get("day"),
                "period": l.get("period") or 1,
                "discipline": l.get("discipline") or "—",
                "workload": l.get("workload_type"),
                "room": l.get("room"),
                "building": l.get("building"),
                "campus": l.get("campus"),
                "subgroup": l.get("subgroup"),
                "approved": bool(l.get("approved", True)),
                "teachers": "; ".join(t for t in teachers if t) or None,
                "key": key[:700],
            })


def stats():
    with get_conn() as conn, dict_cursor(conn) as cur:
        cur.execute(
            'SELECT (SELECT COUNT(*) FROM "group") AS groups, '
            "(SELECT COUNT(*) FROM lesson) AS lessons, "
            "(SELECT COUNT(*) FROM teacher) AS teachers, "
            "(SELECT COUNT(*) FROM department) AS departments, "
            "(SELECT MAX(synced_at) FROM lesson) AS last_sync"
        )
        r = dict(cur.fetchone())
        if r.get("last_sync"):
            r["last_sync"] = r["last_sync"].isoformat()
        return r
