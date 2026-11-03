#!/usr/bin/env python3
"""Fills a guest data file with a year of made-up activity for the README screenshots.

Usage: demo_data.py <guest-data.json> [YYYY-MM-DD]

Takes the file written by .maestro/screenshots-setup.yaml (salary profile and recurring bills already set up in
the app) and adds, for the 12 months up to the given day (default today): the salary profile's pay, a little
freelance income, the recurring bills, and everyday spending. Values are invented and seeded, so every run gives
the same pictures. Never put real figures here.
"""

import calendar
import datetime as dt
import json
import random
import sys

FOOD, SHOPPING, INVESTMENTS = "FOOD", "SHOPPING", "INVESTMENTS"
HOME, TRANSPORT, HEALTH, LEISURE = "starter_home", "starter_transport", "starter_health", "starter_leisure"
RECURRING_DAYS = {"Rent": 1, "Gym": 5, "Internet & phone": 8}
VACATION_DAYS = {8: 10, 12: 3, 4: 2}  # month -> vacation days taken (22 a year in the profile)


def easter(year):
    a, b, c = year % 19, year // 100, year % 100
    d, e = b // 4, b % 4
    f = (b + 8) // 25
    g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30
    i, k = c // 4, c % 4
    l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    month = (h + l - 7 * m + 114) // 31
    day = (h + l - 7 * m + 114) % 31 + 1
    return dt.date(year, month, day)


def holidays(year):
    """Portuguese national holidays."""
    fixed = [(1, 1), (4, 25), (5, 1), (6, 10), (8, 15), (10, 5), (11, 1), (12, 1), (12, 8), (12, 25)]
    e = easter(year)
    return {dt.date(year, m, d) for m, d in fixed} | {e - dt.timedelta(days=2), e, e + dt.timedelta(days=60)}


def working_days(year, month):
    days = calendar.monthrange(year, month)[1]
    off = holidays(year)
    return [dt.date(year, month, d) for d in range(1, days + 1)
            if dt.date(year, month, d).weekday() < 5 and dt.date(year, month, d) not in off]


def instant(day):
    return f"{day.isoformat()}T12:00:00Z"


def main():
    path = sys.argv[1]
    today = dt.date.fromisoformat(sys.argv[2]) if len(sys.argv) > 2 else dt.date.today()
    doc = json.load(open(path))
    rnd = random.Random(2026)

    months = []
    y, m = today.year, today.month
    for _ in range(13):
        months.append((y, m))
        y, m = (y - 1, 12) if m == 1 else (y, m - 1)
    months.reverse()  # 12 full months back plus the current one
    first = dt.date(months[0][0], months[0][1], 1)

    lines = {p["id"]: p for p in doc["plannedIncome"]}
    for p in doc["plannedIncome"]:
        p["startMonth"] = f"{first.year}-{first.month:02d}"

    expenses, income, settings = [], [], []
    seq = iter(range(1, 100000))

    def spend(day, name, cents, category, **extra):
        if day > today:
            return None
        e = {"id": f"demo_e{next(seq)}", "name": name, "amountCents": cents, "categoryId": category,
             "date": instant(day), **extra}
        expenses.append(e)
        return e

    def receive(day, line_id, cents):
        if day >= today:
            return
        ym = f"{day.year}{day.month:02d}"
        income.append({"id": f"pay_{line_id}_{ym}", "source": lines[line_id]["label"], "amountCents": cents,
                       "date": instant(day)})

    for y, m in months:
        last = calendar.monthrange(y, m)[1]
        work = working_days(y, m)
        vacation = VACATION_DAYS.get(m, 0)
        if vacation:
            settings.append({"month": f"{y}-{m:02d}", "vacationDays": vacation, "otherDays": 0})
        payday = work[-1]

        # Pay, on the last working day.
        receive(payday, "profile_net_salary", lines["profile_net_salary"]["amountCents"])
        receive(payday, "profile_duodecimos", lines["profile_duodecimos"]["amountCents"])
        receive(payday, "profile_meal", lines["profile_meal"]["amountCents"] * (len(work) - vacation))
        if m == 6:
            receive(payday, "profile_holiday", lines["profile_holiday"]["amountCents"])
        if m == 12:
            receive(payday, "profile_christmas", lines["profile_christmas"]["amountCents"])
        if m in (3, 7, 10):
            day = dt.date(y, m, 18)
            if day < today:
                income.append({"id": f"demo_i{y}{m:02d}", "source": "Freelance",
                               "amountCents": {3: 42000, 7: 65000, 10: 38000}[m], "date": instant(day)})

        # Recurring bills (the app posts these itself; ids follow its rec_<item>_<yyyyMMdd> pattern).
        for r in doc["recurring"]:
            day = dt.date(y, m, RECURRING_DAYS[r["name"]])
            if day <= today:
                expenses.append({"id": f"rec_{r['id']}_{day:%Y%m%d}", "name": r["name"],
                                 "amountCents": r["amountCents"], "categoryId": r["categoryId"],
                                 "date": instant(day), "needsReview": day > today - dt.timedelta(days=4)})

        def day_in_month():
            return dt.date(y, m, rnd.randint(1, last))

        spend(dt.date(y, m, 2), "ETF", 20000, INVESTMENTS)
        spend(dt.date(y, m, 1), "Train pass", 4000, TRANSPORT)
        spend(dt.date(y, m, 12), "Streaming", 1099, LEISURE)
        for _ in range(rnd.randint(5, 7)):
            spend(day_in_month(), "Groceries", rnd.randint(2500, 9500), FOOD)
        for _ in range(rnd.randint(7, 10)):
            d = rnd.choice(work)
            spend(d, "Lunch", rnd.randint(780, 1350), FOOD, paidWithMealCard=True)
        for _ in range(rnd.randint(1, 3)):
            spend(day_in_month(), "Dinner out", rnd.randint(2800, 6200), FOOD)
        for _ in range(2):
            spend(day_in_month(), "Fuel", rnd.randint(4200, 6100), TRANSPORT)
        for _ in range(rnd.randint(2, 4)):
            spend(day_in_month(), rnd.choice(["Clothes", "Books", "Home goods", "Electronics"]),
                  rnd.randint(1500, 11000), SHOPPING)
        if rnd.random() < 0.6:
            spend(day_in_month(), "Pharmacy", rnd.randint(800, 2600), HEALTH)
        if rnd.random() < 0.5:
            spend(day_in_month(), "Cinema", rnd.randint(1400, 2400), LEISURE)
        if m == 12:
            spend(dt.date(y, m, 20), "Christmas gifts", 31500, SHOPPING)
        if m == 8:
            spend(dt.date(y, m, 10), "Holiday apartment", 54000, LEISURE)
            spend(dt.date(y, m, 10), "Tolls", 3800, TRANSPORT)

    # A shared dinner refunded in full a few months back, and one paid this month still waiting for its refund.
    y, m = months[-5]
    shared = spend(dt.date(y, m, 14), "Birthday dinner", 18400, FOOD)
    assert shared is not None
    spend(dt.date(y, m, 16), "Birthday dinner", 9200, FOOD, isRefund=True, refundOf=shared["id"])
    y, m = months[-2]
    spend(dt.date(y, m, 22), "Concert tickets", 9600, LEISURE, awaitingRefund=True,
          note="Two tickets, Rita pays one")

    for r in doc["recurring"]:
        r["dayOfMonth"] = RECURRING_DAYS[r["name"]]
        r["startDate"] = first.isoformat()
        r["handledThrough"] = today.isoformat()

    doc["expenses"] = expenses
    doc["income"] = income
    doc["monthSettings"] = settings
    # The everyday names repeat monthly on purpose; keep Home free of "Make it recurring?" prompts for them.
    doc["dismissedRecurringSuggestions"] = sorted({e["name"].lower() for e in expenses})
    doc["revision"] = doc.get("revision", 0) + 1
    json.dump(doc, open(path, "w"), ensure_ascii=False, indent=1)
    print(f"{len(expenses)} expenses, {len(income)} income records, {first} to {today}")


if __name__ == "__main__":
    main()
