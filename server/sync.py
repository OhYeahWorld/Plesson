"""Синхронизация внешнего источника pleh.tech (PostgREST) с локальной PostgreSQL.

Сервер — единственный клиент внешнего API: мобильные клиенты ходят только к нам,
а мы отдаём данные из своей БД (клиент-серверная архитектура, Тема 3).
"""
import json
import urllib.parse
import urllib.request

import dal
from config import PLEH_BASE, PLEH_TOKEN

PAGE = 1000          # PostgREST ограничивает выдачу, идём страницами
MAX_PAGES = 40       # страховка от бесконечного цикла


def _get(path: str, params: dict):
    qs = urllib.parse.urlencode(params)
    req = urllib.request.Request(
        f"{PLEH_BASE}/{path}?{qs}",
        headers={
            "apikey": PLEH_TOKEN,
            "Authorization": f"Bearer {PLEH_TOKEN}",
            "Accept-Profile": "api",
            "Accept": "application/json",
            # pleh.tech за WAF и отдаёт 403 без браузерных заголовков
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:157.0) "
                          "Gecko/20100101 Firefox/157.0",
            "Referer": "https://pleh.tech/timetable",
        },
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_all(path: str, select: str, order: str):
    """Постраничная выборка всего справочника."""
    rows, offset = [], 0
    for _ in range(MAX_PAGES):
        page = _get(path, {"select": select, "order": order,
                           "offset": offset, "limit": PAGE})
        rows.extend(page)
        if len(page) < PAGE:
            break
        offset += PAGE
    return rows


def sync_teachers():
    teachers = fetch_all(
        "teachers_current",
        "teacher_slug,teacher_name,departments",
        "teacher_name.asc",
    )
    dal.upsert_teachers_departments(teachers)
    return len(teachers)


def sync_groups():
    groups = fetch_all(
        "groups_current",
        "group_guid,group_name,faculty,program_type,course",
        "group_name.asc",
    )
    for g in groups:
        if g.get("group_guid"):
            dal.upsert_group(g["group_guid"], g.get("group_name") or "",
                             g.get("faculty"), g.get("program_type"), g.get("course"))
    return len(groups)


def sync_week_for_group(guid: str, monday: str, saturday: str):
    """rpc/group_week -> таблица lesson (одна транзакция на группу)."""
    lessons = _get("rpc/group_week", {
        "p_group_guid": guid, "p_from": monday, "p_to": saturday,
        "order": "day.asc,period.asc",
        "select": "day,period,discipline,workload_type,room,building,campus,"
                  "teacher_name,teacher_names,instructor_names,subgroup,approved",
    })
    grp = dal.get_group_by_guid(guid)
    if not grp:
        return 0
    # instructor_names — преподаватели без привязки к дисциплине, не смешиваем
    for l in lessons:
        l.pop("instructor_names", None)
    dal.upsert_lessons(grp["group_id"], lessons)
    return len(lessons)


def sync_week(monday: str, saturday: str, on_progress=None):
    """Синхронизация расписания всех групп на заданную неделю."""
    total = 0
    offset = 0
    while True:
        page = dal.list_groups(limit=200, offset=offset)
        if not page:
            break
        for g in page:
            try:
                n = sync_week_for_group(g["group_guid"], monday, saturday)
                total += n
                if on_progress:
                    on_progress(g["name"], n)
            except Exception:
                pass  # источник может не отдать отдельную группу — пропускаем
        offset += 200
    return total
