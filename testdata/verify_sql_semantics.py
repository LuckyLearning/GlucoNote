"""SQL 聚合结果 vs Kotlin 内存算法的逐值对拍。

`StatsSqlParityTest` 只能验证「判定规则」这种纯函数；
真正容易出错的其实是 SQL 本身（别名映射、`SUM` 空集、`BETWEEN` 边界、
`GROUP BY` 分组、二阶矩换算）。这里用同一批数据在真实 SQLite 上跑真实 SQL，
再用 Python 复刻 Kotlin 侧算法算一遍，逐值比对。

运行：python testdata/verify_sql_semantics.py
"""
import math
import os
import random
import sqlite3
import statistics
import time
from datetime import datetime

ROWS = 5040
SEED = 424242
DAY_MS = 86_400_000
DB = os.path.join(os.path.dirname(__file__), "_semantics_check.db")

F_MIN, F_MAX, P_MIN, P_MAX = 4.4, 7.0, 4.4, 10.0
FASTING = "fasting"
PERIODS = ["fasting", "breakfast", "lunch", "dinner", "sleep"]

failures = []


def check(name, actual, expected, tol=1e-6):
    ok = abs(actual - expected) <= tol
    mark = "OK " if ok else "FAIL"
    print(f"  [{mark}] {name}: sql={actual!r} kotlin={expected!r}")
    if not ok:
        failures.append(name)


def level_of(value, period_key):
    """复刻 StatsUtils.levelOf()：空腹用 fasting 区间，其余（含睡前）用 post 区间"""
    lo, hi = (F_MIN, F_MAX) if period_key == FASTING else (P_MIN, P_MAX)
    if value < lo:
        return "low"
    if value > hi:
        return "high"
    return "normal"


def build():
    if os.path.exists(DB):
        os.remove(DB)
    con = sqlite3.connect(DB)
    con.executescript(
        """
        CREATE TABLE `records` (
            `id` TEXT NOT NULL, `measureTime` INTEGER NOT NULL, `value` REAL NOT NULL,
            `period` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        );
        CREATE INDEX `idx_measure_time_id` ON `records` (`measureTime`, `id`);
        CREATE INDEX `idx_period_measure_time_id` ON `records` (`period`, `measureTime`, `id`);
        CREATE INDEX `idx_cover_time_value_period` ON `records` (`measureTime`, `value`, `period`);
        """
    )
    rnd = random.Random(SEED)
    now = int(time.time() * 1000)
    start = now - 1008 * DAY_MS  # 约 2.75 年
    rows = []
    for i in range(ROWS):
        ts = start + int(i * (1008 * DAY_MS) / ROWS) + rnd.randint(0, 3600_000)
        p = PERIODS[i % 5]
        base = 6.6 if p == FASTING else 7.4
        v = round(max(2.5, min(22.0, rnd.gauss(base, 1.9))), 1)
        rows.append((f"r{i:06d}", ts, v, p, "", ts))
    con.executemany("INSERT INTO records VALUES (?,?,?,?,?,?)", rows)
    con.commit()
    con.execute("ANALYZE")
    con.commit()
    return con, now, rows


def parity_rate(con, now, rows):
    print("\n=== 1. 达标率 rateByPeriod（7 天窗口）===")
    frm = now - 7 * DAY_MS
    got = con.execute(
        """
        SELECT period, COUNT(*) AS total,
               SUM(CASE
                     WHEN period = 'fasting' AND value BETWEEN ? AND ? THEN 1
                     WHEN period <> 'fasting' AND value BETWEEN ? AND ? THEN 1
                     ELSE 0 END) AS inRange
        FROM records WHERE measureTime BETWEEN ? AND ? GROUP BY period
        """,
        (F_MIN, F_MAX, P_MIN, P_MAX, frm, now),
    ).fetchall()
    sql_map = {p: (t, r) for p, t, r in got}

    # Kotlin: scopedRecords(7 天) → computeRateByPeriod
    scoped = [r for r in rows if frm <= r[1] <= now]
    for p in PERIODS:
        recs = [r for r in scoped if r[3] == p]
        expected_total = len(recs)
        expected_in = sum(1 for r in recs if level_of(r[2], p) == "normal")
        if expected_total == 0:
            check(f"{p} total（SQL 无行 = Kotlin 缺键）", 0 if p not in sql_map else 1, 0)
            continue
        actual_total, actual_in = sql_map[p]
        check(f"{p} total", actual_total, expected_total)
        check(f"{p} inRange", actual_in, expected_in)


def parity_range(con, now, rows):
    print("\n=== 2. 区间聚合 rangeStats + 二阶矩标准差（30 天窗口）===")
    frm = now - 30 * DAY_MS
    cnt, avg, mx, mn, avg_sq = con.execute(
        """
        SELECT COUNT(*), AVG(value), MAX(value), MIN(value), AVG(value*value)
        FROM records WHERE measureTime BETWEEN ? AND ?
        """,
        (frm, now),
    ).fetchone()

    vals = [r[2] for r in rows if frm <= r[1] <= now]
    check("count", cnt, len(vals))
    check("avg", avg, statistics.fmean(vals), 1e-6)
    check("max", mx, max(vals))
    check("min", mn, min(vals))
    # 内存版：variance = mean((x-avg)^2)；SQL 版：avgSq - avg^2
    expected_std = statistics.pstdev(vals)
    sql_std = math.sqrt(max(0.0, avg_sq - avg * avg))
    check("std", sql_std, expected_std, 1e-4)

    # 空窗口：SQL 必须返回一行 count=0 且 avg 为 NULL
    empty = con.execute(
        "SELECT COUNT(*), AVG(value), MAX(value), MIN(value), AVG(value*value) "
        "FROM records WHERE measureTime BETWEEN ? AND ?",
        (now + DAY_MS, now + 2 * DAY_MS),
    ).fetchone()
    print(f"  [空窗口] count={empty[0]} avg={empty[1]!r} max={empty[2]!r} "
          f"avgSq={empty[4]!r} → 期望 (0, None, None, None, None)")
    if empty != (0, None, None, None, None):
        failures.append("空窗口返回形态")


def parity_daily(con, now, rows):
    print("\n=== 3. 趋势按日降采样 dailyTrend（1 年窗口）===")
    frm = now - 365 * DAY_MS
    # 与 Kotlin 一致：用当前时区偏移把 UTC 毫秒对齐到本地日
    tz_shift = -time.timezone * 1000
    got = con.execute(
        """
        SELECT (measureTime + ?) / 86400000 AS day, MIN(measureTime) AS firstTs,
               AVG(value) AS a, MAX(value) AS mx, MIN(value) AS mn, COUNT(*) AS c
        FROM records WHERE measureTime BETWEEN ? AND ? GROUP BY day ORDER BY day
        """,
        (tz_shift, frm, now),
    ).fetchall()

    # Python 版：按本地自然日分组
    groups = {}
    for r in rows:
        if not (frm <= r[1] <= now):
            continue
        key = datetime.fromtimestamp(r[1] / 1000).strftime("%Y-%m-%d")
        groups.setdefault(key, []).append(r)

    print(f"  SQL 桶数 = {len(got)}, Kotlin 桶数 = {len(groups)}")
    if len(got) != len(groups):
        failures.append("按日分桶数量")
    else:
        ok = True
        for (day, first_ts, a, mx, mn, c), (label, recs) in zip(got, groups.items()):
            vals = [r[2] for r in recs]
            if c != len(vals) or abs(a - statistics.fmean(vals)) > 1e-4 \
                    or mx != max(vals) or mn != min(vals):
                ok = False
                print(f"  [FAIL] {label}: sql=({c},{a:.4f},{mx},{mn}) "
                      f"kotlin=({len(vals)},{statistics.fmean(vals):.4f},{max(vals)},{min(vals)})")
                break
            # firstTs 必须落在同一本地日，否则图表标签会差一天
            if datetime.fromtimestamp(first_ts / 1000).strftime("%Y-%m-%d") != label:
                ok = False
                print(f"  [FAIL] {label}: firstTs 落在 "
                      f"{datetime.fromtimestamp(first_ts / 1000).strftime('%Y-%m-%d')}")
                break
        print(f"  [{'OK ' if ok else 'FAIL'}] 每个桶的 (count, avg, max, min, 本地日标签)")
        if not ok:
            failures.append("按日分桶内容")

    # 降采样门槛：1 年窗口的点数
    raw = con.execute(
        "SELECT COUNT(*) FROM records WHERE measureTime BETWEEN ? AND ?", (frm, now)
    ).fetchone()[0]
    print(f"  1 年窗口原始点数 = {raw} → 需要降采样 = {raw > 400}（阈值 400）")
    if raw > 400 and len(got) > 400:
        failures.append("降采样后仍超阈值")


def parity_page(con, now, rows):
    print("\n=== 4. keyset 分页与全量排序一致性 ===")
    ordered = sorted(rows, key=lambda r: (-r[1], r[0].encode()))
    seen = []
    limit = 25
    page = con.execute(
        "SELECT * FROM records ORDER BY measureTime DESC, id DESC LIMIT ?", (limit,)
    ).fetchall()
    seen.extend(page)
    while True:
        ct, cid = page[-1][1], page[-1][0]
        page = con.execute(
            "SELECT * FROM records WHERE measureTime < ? OR (measureTime = ? AND id < ?) "
            "ORDER BY measureTime DESC, id DESC LIMIT ?",
            (ct, ct, cid, limit),
        ).fetchall()
        if not page:
            break
        seen.extend(page)
    sql_ids = [r[0] for r in seen]
    expected_ids = [r[0] for r in ordered]
    check("分页遍历条数", len(sql_ids), len(expected_ids))
    print(f"  [{'OK ' if sql_ids == expected_ids else 'FAIL'}] 分页顺序 == 全量倒序")
    if sql_ids != expected_ids:
        failures.append("分页顺序")

    # 同时刻多条（同一 measureTime）时 keyset 不能漏也不能重复
    dup_ts = now - 5000
    con.executemany(
        "INSERT INTO records VALUES (?,?,?,?,?,?)",
        [(f"dup{i}", dup_ts, 6.0, "lunch", "", dup_ts) for i in range(60)],
    )
    con.commit()
    got = []
    page = con.execute(
        "SELECT * FROM records WHERE measureTime <= ? ORDER BY measureTime DESC, id DESC LIMIT 10",
        (dup_ts,),
    ).fetchall()
    while page:
        got.extend(page)
        ct, cid = page[-1][1], page[-1][0]
        page = con.execute(
            "SELECT * FROM records WHERE measureTime < ? OR (measureTime = ? AND id < ?) "
            "ORDER BY measureTime DESC, id DESC LIMIT 10",
            (ct, ct, cid),
        ).fetchall()
        if page and page[-1][1] > dup_ts - DAY_MS:
            pass
    dup_got = [r[0] for r in got if r[1] == dup_ts]
    print(f"  [{'OK ' if len(dup_got) == 60 and len(set(dup_got)) == 60 else 'FAIL'}] "
          f"同一时刻 60 条：取到 {len(dup_got)} 条，去重后 {len(set(dup_got))} 条")
    if not (len(dup_got) == 60 and len(set(dup_got)) == 60):
        failures.append("同一时刻 keyset 分页")


def main():
    con, now, rows = build()
    print(f"行数 = {len(rows)}")
    parity_rate(con, now, rows)
    parity_range(con, now, rows)
    parity_daily(con, now, rows)
    parity_page(con, now, rows)
    con.close()
    os.remove(DB)
    print("\n" + ("全部通过。" if not failures else f"失败项：{failures}"))
    raise SystemExit(1 if failures else 0)


if __name__ == "__main__":
    main()
