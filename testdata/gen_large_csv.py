#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 GlucoNote 导入用 CSV 压测数据（格式与 util/Csv.kt 完全一致）。

用法：
  python3 gen_large_csv.py                          # 默认 10 年 × 5 次/天 ≈ 18k 条
  python3 gen_large_csv.py --years 10 --per-day 55   # ≈ 200k 条
  python3 gen_large_csv.py --out big.csv --seed 1

输出：UTF-8 带 BOM，表头 id,measure_time,value,period,period_label,note
"""
import argparse
import datetime
import math
import random
import uuid

PERIODS = [
    ("fasting", "空腹", (6, 8), 6.35, 0.55),
    ("breakfast", "早餐后", (9, 10), 9.55, 1.30),
    ("lunch", "午餐后", (13, 14), 9.85, 1.40),
    ("dinner", "晚餐后", (19, 20), 9.70, 1.40),
    ("sleep", "睡前", (22, 23), 7.35, 0.80),
]
NOTES = ["", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
         "饭后散步30分钟", "主食减半", "吃了水果", "加班晚睡", "感冒",
         "聚餐", "应酬喝了酒", "运动后测", "空腹未满8小时", "清淡饮食"]


def season(d):
    return 0.28 * math.cos(2 * math.pi * (d.timetuple().tm_yday - 15) / 365.0)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--years", type=int, default=10)
    ap.add_argument("--per-day", type=int, default=5, help="每天测量次数（5 的倍数时各时段更均匀）")
    ap.add_argument("--out", default="gluconote_large.csv")
    ap.add_argument("--seed", type=int, default=20261027)
    ap.add_argument("--end", default="2026-10-27", help="最后一天 YYYY-MM-DD")
    a = ap.parse_args()

    random.seed(a.seed)
    end = datetime.date.fromisoformat(a.end)
    start = end - datetime.timedelta(days=365 * a.years)
    now = datetime.datetime.now() if end == datetime.date.today() else None

    def esc(s):
        s = str(s)
        return '"' + s.replace('"', '""') + '"' if any(c in s for c in ',"\n') else s

    total = 0
    with open(a.out, "w", encoding="utf-8", newline="\n") as f:
        f.write("\ufeffid,measure_time,value,period,period_label,note\n")
        day = start
        span = max(1, (end - start).days)
        while day <= end:
            t = (day - start).days / span
            tf = 0.30 - 0.60 * t
            sf = season(day)
            # 每天 per-day 次：前 5 次覆盖五个时段，超出部分随机时段重测
            for i in range(a.per_day):
                key, label, (h0, h1), base, sd = PERIODS[i % 5] if i < 5 else random.choice(PERIODS)
                ts = datetime.datetime(
                    day.year, day.month, day.day,
                    random.randint(h0, h1 - 1), random.randint(0, 59), random.randint(0, 59)
                )
                if now and ts > now:
                    continue
                v = random.gauss(base + sf + tf, sd)
                r = random.random()
                if key in ("breakfast", "lunch", "dinner") and r < 0.06:
                    v += random.uniform(1.5, 3.5)
                elif r < 0.05 and key in ("fasting", "sleep"):
                    v -= random.uniform(0.9, 2.4)
                if random.random() < 0.004:
                    v = random.choice([3.0, 3.2, 13.4, 14.2, 15.6])
                v = round(max(2.8, min(16.5, v)), 1)
                note = random.choice(NOTES)
                f.write(",".join(esc(x) for x in [
                    uuid.UUID(int=random.getrandbits(128), version=4),
                    ts.strftime("%Y-%m-%d %H:%M:%S"), v, key, label, note
                ]) + "\n")
                total += 1
            day += datetime.timedelta(days=1)

    print("写入 %s：%d 条" % (a.out, total))


if __name__ == "__main__":
    main()
