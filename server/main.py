"""PLesson — серверная часть (Тема 3): REST API поверх PostgreSQL.

Эндпоинты:
  GET  /api/health                     — проверка связности
  GET  /api/groups?q=&limit=&offset=   — справочник групп (поиск, пагинация)
  GET  /api/schedule/{guid}?date=      — расписание группы на неделю (Пн–Сб)
  GET  /api/teachers?name=             — преподаватель: кафедры, ФИО
  GET  /api/teacher-lessons?name=&date=— доп. информация: занятия преподавателя
  POST /api/sync                       — синхронизация с pleh.tech (админ)
  GET  /api/stats                      — агрегатный отчёт по БД (Тема 1: отчёты)

Единый обработчик ошибок: клиент всегда получает JSON {error, detail}.
"""
import datetime as dt
from zoneinfo import ZoneInfo

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

import dal
import sync
from config import API_TITLE, API_VERSION

MSK = ZoneInfo("Europe/Moscow")
PERIODS = {  # расписание пар (Плехановский университет)
    1: "09:00–10:35", 2: "10:45–12:20", 3: "13:20–14:55", 4: "15:05–16:40",
    5: "16:50–18:25", 6: "18:35–20:10", 7: "20:15–21:50", 8: "09:00–10:35",
}

app = FastAPI(title=API_TITLE, version=API_VERSION)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_methods=["*"], allow_headers=["*"],
)


@app.exception_handler(Exception)
async def unhandled_error_handler(request, exc):
    """Не «падаем», а отдаём осмысленное сообщение (Тема 3.3)."""
    return JSONResponse(status_code=500,
                        content={"error": "Внутренняя ошибка сервера",
                                 "detail": str(exc)})


def week_bounds(date_str: str | None):
    """Понедельник–суббота недели, содержащей date (по Москве)."""
    if date_str:
        try:
            d = dt.date.fromisoformat(date_str)
        except ValueError:
            raise HTTPException(400, "Некорректная дата, ожидается ГГГГ-ММ-ДД")
    else:
        d = dt.datetime.now(MSK).date()
    monday = d - dt.timedelta(days=d.weekday())
    return monday.isoformat(), (monday + dt.timedelta(days=5)).isoformat(), d


# ------------------------------- health --------------------------------------

@app.get("/api/health")
def health():
    try:
        dal.stats()
        return {"status": "ok", "database": "connected"}
    except Exception as e:
        raise HTTPException(503, f"База данных недоступна: {e}")


# ------------------------------- groups ---------------------------------------

@app.get("/api/groups")
def get_groups(q: str = Query("", max_length=100),
               limit: int = Query(50, ge=1, le=200),
               offset: int = Query(0, ge=0)):
    try:
        items = dal.list_groups(q, limit, offset)
        total = dal.count_groups(q)
        return {"items": items, "total": total, "limit": limit, "offset": offset}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(500, f"Ошибка чтения групп: {e}")


# ------------------------------ schedule -------------------------------------

@app.get("/api/schedule/{guid}")
def get_schedule(guid: str, date: str | None = None, force: bool = False):
    """Неделя Пн–Сб. Сначала своя БД; если пусто и force — тянем из источника."""
    frm, to, today = week_bounds(date)
    try:
        rows = dal.week_schedule(guid, frm, to)
    except Exception as e:
        raise HTTPException(500, f"Ошибка чтения расписания: {e}")

    if not rows and force:
        grp = dal.get_group_by_guid(guid)
        if not grp:
            raise HTTPException(404, "Группа не найдена в базе. Обновите список групп (POST /api/sync).")
        try:
            sync.sync_week_for_group(guid, frm, to)
            rows = dal.week_schedule(guid, frm, to)
        except Exception as e:
            raise HTTPException(502, f"Источник данных недоступен: {e}")

    days = {}
    for r in rows:
        day = r["day"]
        days.setdefault(day, []).append({
            **r,
            "time": PERIODS.get(r["period"], ""),
        })
    result = []
    d0 = dt.date.fromisoformat(frm)
    for i in range(6):  # Понедельник … Суббота — всегда 6 карточек дней
        ds = (d0 + dt.timedelta(days=i)).isoformat()
        result.append({"date": ds, "lessons": sorted(
            days.get(ds, []), key=lambda x: (x["period"] or 0))})
    return {"group_guid": guid, "week_from": frm, "week_to": to,
            "today": today.isoformat(), "days": result}


# ------------------------------ teachers -------------------------------------

@app.get("/api/teachers")
def get_teachers(name: str = Query(..., min_length=2, max_length=200)):
    try:
        return {"items": dal.find_teacher(name)}
    except Exception as e:
        raise HTTPException(500, f"Ошибка чтения преподавателей: {e}")


@app.get("/api/teacher-lessons")
def get_teacher_lessons(name: str = Query(..., min_length=2, max_length=200),
                        date: str | None = None):
    frm, to, _ = week_bounds(date)
    try:
        return {"from": frm, "to": to,
                "items": dal.teacher_lessons(name, frm, to)}
    except Exception as e:
        raise HTTPException(500, f"Ошибка чтения занятий преподавателя: {e}")


# -------------------------------- sync ----------------------------------------

@app.post("/api/sync")
def do_sync(groups: bool = True, teachers: bool = True, weeks: int = 2):
    """Обновление справочников и расписания ближайших недель из pleh.tech."""
    report = {}
    try:
        if teachers:
            report["teachers"] = sync.sync_teachers()
        if groups:
            report["groups"] = sync.sync_groups()
        frm, _, _ = week_bounds(None)
        base = dt.date.fromisoformat(frm)
        lessons = 0
        mondays = []
        for w in range(-1, weeks + 1):  # прошлая, текущая и следующие недели
            m = base + dt.timedelta(days=7 * w)
            mondays.append((m.isoformat(), (m + dt.timedelta(days=5)).isoformat()))
        for (mf, mt) in mondays:
            lessons += sync.sync_week(mf, mt)
        report["lessons"] = lessons
        report["weeks"] = len(mondays)
        return {"status": "ok", "report": report, "stats": dal.stats()}
    except Exception as e:
        raise HTTPException(502, f"Синхронизация не удалась: {e}")


@app.get("/api/stats")
def get_stats():
    try:
        return dal.stats()
    except Exception as e:
        raise HTTPException(500, f"Ошибка формирования отчёта: {e}")
