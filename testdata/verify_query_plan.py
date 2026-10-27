"""验证阶段 A/B 的查询计划与耗时（20 万行真实 SQLite 库）。

用途：确认新加的 3 个索引真的被优化器用上，而不是"加了索引但仍全表扫描"。
在 Room 之外用 Python 内置 sqlite3 复刻同一份 schema 与同一批 SQL，
因为 EXPLAIN QUERY PLAN 与 SQLite 版本/编译选项无关太多，结论可迁移。

运行：python testdata/verify_query_plan.py
"""
import os
import random
import sqlite3
import time

ROWS = 200_000
DAYS = 40000  # 20 万行 / 5 次每天 ≈ 11 年
SEED = 20261027
DAY_MS = 86_400_000

DB_PATH = os.path.join(os.path.dirname(__file__), "_plan_check.db")


def build_db():
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
    con = sqlite3.connect(DB_PATH)
    # 与 Room 生成的 schema 完全一致
    con.executescript(
        """
        CREATE TABLE IF NOT EXISTS `records` (
            `id` TEXT NOT NULL,
            `measureTime` INTEGER NOT NULL,
            `value` REAL NOT NULL,
            `period` TEXT NOT NULL,
            `note` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        );
        CREATE INDEX IF NOT EXISTS `idx_measure_time_id` ON `records` (`measureTime`, `id`);
        CREATE INDEX IF NOT EXISTS `idx_period_measure_time_id` ON `records` (`period`, `measureTime`, `id`);
        CREATE INDEX IF NOT EXISTS `idx_cover_time_value_period` ON `records` (`measureTime`, `value`, `period`);
        """
    )
    rnd = random.Random(SEED)
    periods = ["fasting", "breakfast", "lunch", "dinner", "sleep"]
    now = int(time.time() * 1000)
    start = now - DAYS * DAY_MS
    batch = []
    for i in range(ROWS):
        ts = start + i * (DAYS * DAY_MS // ROWS)
        # 空腹偏高一点、餐后偏低一点，制造真实分布倾斜（优化器选错索引的典型诱因）
        base = 6.6 if periods[i % 5] == "fasting" else 7.4
        v = round(max(2.5, min(22.0, rnd.gauss(base, 1.8))), 1)
        batch.append(
            (
                f"id-{i:07d}",
                ts,
                v,
                periods[i % 5],
                "" if i % 7 else "备注",
                ts,
            )
        )
    con.executemany("INSERT INTO records VALUES (?,?,?,?,?,?)", batch)
    con.commit()
    con.execute("ANALYZE")
    con.commit()
    return con, now


def plan(con, title, sql, args=()):
    print(f"\n--- {title}")
    for row in con.execute("EXPLAIN QUERY PLAN " + sql, args):
        print("    ", row[-1])


def bench(con, title, sql, args=(), rounds=20):
    # 预热
    con.execute(sql, args).fetchall()
    t0 = time.perf_counter()
    for _ in range(rounds):
        con.execute(sql, args).fetchall()
    ms = (time.perf_counter() - t0) * 1000 / rounds
    print(f"     {title}: {ms:.3f} ms")
    return ms


def main():
    con, now = build_db()
    n = con.execute("SELECT COUNT(*) FROM records").fetchone()[0]
    print(f"行数 = {n}, 库大小 = {os.path.getsize(DB_PATH)/1024/1024:.1f} MB")

    cursor_time = now - 100 * DAY_MS
    cursor_id = "id-0000500"

    # ---------------- 分页
    plan(con, "首页第一页",
         "SELECT * FROM records ORDER BY measureTime DESC, id DESC LIMIT 25")
    plan(con, "全部·续页 (keyset)",
         "SELECT * FROM records WHERE measureTime < ? OR (measureTime = ? AND id < ?) "
         "ORDER BY measureTime DESC, id DESC LIMIT 25",
         (cursor_time, cursor_time, cursor_id))
    plan(con, "标签·第一页",
         "SELECT * FROM records WHERE period = ? ORDER BY measureTime DESC, id DESC LIMIT 25",
         ("lunch",))
    plan(con, "标签·续页 (keyset)",
         "SELECT * FROM records WHERE period = ? AND "
         "(measureTime < ? OR (measureTime = ? AND id < ?)) "
         "ORDER BY measureTime DESC, id DESC LIMIT 25",
         ("lunch", cursor_time, cursor_time, cursor_id))

    # ---------------- 聚合
    start_today = now - DAY_MS
    week_ago = now - 7 * DAY_MS
    plan(con, "首页概览",
         "SELECT COALESCE(SUM(CASE WHEN measureTime >= ? THEN 1 ELSE 0 END), 0), "
         "AVG(CASE WHEN measureTime >= ? THEN value END) FROM records WHERE measureTime >= ?",
         (start_today, week_ago, week_ago))
    plan(con, "达标率（7 天）",
         "SELECT period, COUNT(*), SUM(CASE "
         "WHEN period = 'fasting' AND value BETWEEN ? AND ? THEN 1 "
         "WHEN period <> 'fasting' AND value BETWEEN ? AND ? THEN 1 ELSE 0 END) "
         "FROM records WHERE measureTime BETWEEN ? AND ? GROUP BY period",
         (4.4, 7.0, 4.4, 10.0, week_ago, now))
    plan(con, "区间聚合（30 天）",
         "SELECT COUNT(*), AVG(value), MAX(value), MIN(value), AVG(value*value) "
         "FROM records WHERE measureTime BETWEEN ? AND ?",
         (now - 30 * DAY_MS, now))
    plan(con, "趋势原始点（1 年）",
         "SELECT measureTime, value, period FROM records "
         "WHERE measureTime BETWEEN ? AND ? ORDER BY measureTime ASC",
         (now - 365 * DAY_MS, now))
    plan(con, "趋势按日降采样（1 年）",
         "SELECT (measureTime + ?) / 86400000 AS day, MIN(measureTime), AVG(value), "
         "MAX(value), MIN(value), COUNT(*) FROM records "
         "WHERE measureTime BETWEEN ? AND ? GROUP BY day ORDER BY day",
         (8 * 3600 * 1000, now - 365 * DAY_MS, now))
    plan(con, "变更令牌（已弃用，见下）",
         "SELECT COUNT(*), COALESCE(MAX(createdAt), 0) FROM records")

    # ---------------- 耗时
    print("\n=== 耗时（20 次平均）===")
    bench(con, "首页第一页 25 条",
          "SELECT * FROM records ORDER BY measureTime DESC, id DESC LIMIT 25")
    bench(con, "全部·续页",
          "SELECT * FROM records WHERE measureTime < ? OR (measureTime = ? AND id < ?) "
          "ORDER BY measureTime DESC, id DESC LIMIT 25",
          (cursor_time, cursor_time, cursor_id))
    bench(con, "标签·续页",
          "SELECT * FROM records WHERE period = ? AND "
          "(measureTime < ? OR (measureTime = ? AND id < ?)) "
          "ORDER BY measureTime DESC, id DESC LIMIT 25",
          ("lunch", cursor_time, cursor_time, cursor_id))
    bench(con, "首页概览（7 天窗口）",
          "SELECT COALESCE(SUM(CASE WHEN measureTime >= ? THEN 1 ELSE 0 END), 0), "
          "AVG(CASE WHEN measureTime >= ? THEN value END) FROM records WHERE measureTime >= ?",
          (start_today, week_ago, week_ago))
    bench(con, "达标率（7 天）",
          "SELECT period, COUNT(*), SUM(CASE "
          "WHEN period = 'fasting' AND value BETWEEN ? AND ? THEN 1 "
          "WHEN period <> 'fasting' AND value BETWEEN ? AND ? THEN 1 ELSE 0 END) "
          "FROM records WHERE measureTime BETWEEN ? AND ? GROUP BY period",
          (4.4, 7.0, 4.4, 10.0, week_ago, now))
    bench(con, "区间聚合（30 天）",
          "SELECT COUNT(*), AVG(value), MAX(value), MIN(value), AVG(value*value) "
          "FROM records WHERE measureTime BETWEEN ? AND ?",
          (now - 30 * DAY_MS, now))
    bench(con, "趋势原始点（1 年）",
          "SELECT measureTime, value, period FROM records "
          "WHERE measureTime BETWEEN ? AND ? ORDER BY measureTime ASC",
          (now - 365 * DAY_MS, now), rounds=5)
    bench(con, "趋势按日降采样（1 年）",
          "SELECT (measureTime + ?) / 86400000 AS day, MIN(measureTime), AVG(value), "
          "MAX(value), MIN(value), COUNT(*) FROM records "
          "WHERE measureTime BETWEEN ? AND ? GROUP BY day ORDER BY day",
          (8 * 3600 * 1000, now - 365 * DAY_MS, now), rounds=5)
    bench(con, "变更令牌（已弃用 → 改用 invalidationTrackerFlow，0 查询开销）",
          "SELECT COUNT(*), COALESCE(MAX(createdAt), 0) FROM records", rounds=5)
    bench(con, "分桶输入（按日 60 天窗口，整行）",
          "SELECT * FROM records WHERE measureTime BETWEEN ? AND ? ORDER BY measureTime ASC",
          (now - 60 * DAY_MS, now), rounds=5)

    # 对照：没有 measureTime 索引时会怎样（模拟旧版）。
    # 必须换新连接 —— Python sqlite3 会缓存已编译语句，同连接上 DROP INDEX 后
    # EXPLAIN 可能仍打印旧计划（耗时才是真实的）。
    con.execute("DROP INDEX idx_measure_time_id")
    con.execute("DROP INDEX idx_cover_time_value_period")
    con.commit()
    con.close()
    con = sqlite3.connect(DB_PATH)
    print("\n=== 对照：仅保留 period 索引（模拟旧版无 measureTime 索引）===")
    plan(con, "无索引·首页第一页",
         "SELECT * FROM records ORDER BY measureTime DESC, id DESC LIMIT 25")
    bench(con, "无索引·首页第一页 25 条",
          "SELECT * FROM records ORDER BY measureTime DESC, id DESC LIMIT 25", rounds=5)
    bench(con, "无索引·区间聚合（30 天）",
          "SELECT COUNT(*), AVG(value), MAX(value), MIN(value), AVG(value*value) "
          "FROM records WHERE measureTime BETWEEN ? AND ?",
          (now - 30 * DAY_MS, now), rounds=5)

    con.close()
    os.remove(DB_PATH)
    print("\n完成。")


if __name__ == "__main__":
    main()
