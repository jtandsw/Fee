#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""重置开发库并灌入演示数据。

做的事：
  1. 从 application-dev.yml + application-local.yml 读取数据库连接（不打印口令）；
  2. 用 mysqldump 把当前库备份到 backups/ 目录（默认执行，--no-backup 可跳过）；
  3. 依次执行 db/schema.sql（重建库表）→ db/data.sql（基础种子）→ db/demo-data.sql（演示业务数据）；
  4. 依据库中真实已付订单号生成一份对账账单 CSV，供"对账管理-上传账单"直接演示；
  5. 打印各表行数与财务口径（应收/实收/欠费/缴费率）。

用法：
    python scripts/reset_demo_data.py                # 备份 + 重建 + 灌演示数据
    python scripts/reset_demo_data.py --no-backup    # 不备份（谨慎）
    python scripts/reset_demo_data.py --data-only    # 保留库表结构，只清空业务数据后重灌

依赖：pymysql（pip install pymysql），mysqldump 需在 PATH 或使用 --mysqldump 指定。
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
from datetime import date, datetime, timedelta
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "backend" / "src" / "main" / "resources"
BACKUP_DIR = ROOT / "backups"
SAMPLE_DIR = ROOT / "docs" / "samples"

BUSINESS_TABLES = [
    "reconciliation_detail",
    "reconciliation_batch",
    "refund_record",
    "payment_order",
    "notice_read",
    "notice",
    "payment_item",
    "student",
    "operation_log",
    "sys_user",
]


def resolve_placeholder(value):
    """把 Spring 的 ${ENV:default} / ${ENV} 占位符解析成实际值（环境变量优先）。"""
    if value is None:
        return ""
    full = re.fullmatch(r"\$\{([^:}]+):?([^}]*)\}", value.strip())
    if full:
        return os.environ.get(full.group(1)) or full.group(2)
    return value.strip()


def read_db_config():
    """解析 yml 拿连接信息；口令只从 local 配置取，不落日志。"""
    dev = (RES / "application-dev.yml").read_text(encoding="utf-8")
    local_path = RES / "application-local.yml"
    local = local_path.read_text(encoding="utf-8") if local_path.exists() else ""

    url = resolve_placeholder(re.search(r"url:\s*(\S+)", dev).group(1))
    user = resolve_placeholder(re.search(r"username:\s*(\S+)", dev).group(1))

    pwd_match = re.search(r"^\s*password:\s*(\S+)\s*$", local, re.M)
    if pwd_match:
        password = resolve_placeholder(pwd_match.group(1))
    else:
        password = ""
    if not password:
        password = os.environ.get("DB_PASSWORD", "")
    if not password:
        sys.exit("未找到数据库口令：请在 application-local.yml 配置 password，或设置环境变量 DB_PASSWORD")

    m = re.search(r"//([^:/]+):(\d+)/(\w+)", url)
    if not m:
        sys.exit(f"无法解析 JDBC URL: {url}")
    return {
        "host": m.group(1),
        "port": int(m.group(2)),
        "database": m.group(3),
        "user": user,
        "password": password,
    }


def backup(cfg):
    dump = shutil.which("mysqldump") or r"C:\Program Files\MySQL\MySQL Server 8.2\bin\mysqldump.exe"
    if not Path(dump).exists():
        print("! 未找到 mysqldump，跳过备份")
        return None
    BACKUP_DIR.mkdir(exist_ok=True)
    target = BACKUP_DIR / f"campus_payment_{datetime.now():%Y%m%d_%H%M%S}.sql"
    env = dict(os.environ, MYSQL_PWD=cfg["password"])
    with open(target, "wb") as fh:
        proc = subprocess.run(
            [dump, "-h", cfg["host"], "-P", str(cfg["port"]), "-u", cfg["user"],
             "--databases", cfg["database"], "--single-transaction", "--default-character-set=utf8mb4"],
            stdout=fh, stderr=subprocess.PIPE, env=env,
        )
    if proc.returncode != 0:
        target.unlink(missing_ok=True)
        sys.exit("备份失败：" + proc.stderr.decode("utf-8", "ignore")[:400])
    print(f"[1/5] 已备份当前库 -> {target.relative_to(ROOT)}  ({target.stat().st_size / 1024:.0f} KB)")
    return target


def run_sql_file(conn, path):
    sql = path.read_text(encoding="utf-8")
    with conn.cursor() as cur:
        # 逐条执行，避免依赖 MULTI_STATEMENTS 的开关差异
        for stmt in split_statements(sql):
            cur.execute(stmt)
            if cur.description:
                cur.fetchall()
    conn.commit()
    print(f"      执行 {path.name} 完成")


def split_statements(sql):
    """按分号切分，跳过注释行与空语句；本项目脚本不含存储过程，够用。"""
    statements, buf = [], []
    for line in sql.splitlines():
        stripped = line.strip()
        if stripped.startswith("--") or stripped.startswith("#"):
            continue
        buf.append(line)
        if stripped.endswith(";"):
            stmt = "\n".join(buf).strip().rstrip(";").strip()
            buf = []
            if stmt:
                statements.append(stmt)
    tail = "\n".join(buf).strip().rstrip(";").strip()
    if tail:
        statements.append(tail)
    return statements


def truncate_business_tables(conn):
    with conn.cursor() as cur:
        cur.execute("SET FOREIGN_KEY_CHECKS = 0")
        for table in BUSINESS_TABLES:
            cur.execute(f"TRUNCATE TABLE {table}")
        cur.execute("SET FOREIGN_KEY_CHECKS = 1")
    conn.commit()
    print("      已清空全部业务表（含账号/学籍/项目/通知），改由 data.sql + demo-data.sql 重灌")


def build_sample_bill(conn):
    """用库里真实已付订单生成一份可上传的账单 CSV：5 笔一致 + 1 笔渠道多账 + 1 笔金额差异。"""
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT o.order_no, o.transaction_no, o.paid_amount
            FROM payment_order o
            WHERE o.status IN ('PAID', 'PARTIAL_REFUND') AND o.transaction_no IS NOT NULL
            ORDER BY o.paid_at, o.id
            LIMIT 5
            """
        )
        rows = cur.fetchall()

    if not rows:
        print("! 没有已付订单，跳过账单样例生成")
        return None

    SAMPLE_DIR.mkdir(parents=True, exist_ok=True)
    bill_date = date.today() - timedelta(days=1)
    target = SAMPLE_DIR / f"对账账单-MOCK-{bill_date:%Y%m%d}.csv"

    lines = ["订单号,渠道流水号,金额"]
    for order_no, txn, amount in rows:
        lines.append(f"{order_no},{txn},{Decimal(amount):.2f}")
    # 渠道多账：本地不存在
    lines.append(f",MOCK99{bill_date:%m%d}0001,5800.00")
    # 金额不一致：取第一笔真实订单，账单金额少 20 元
    order_no, txn, amount = rows[0]
    lines.append(f"{order_no},{txn},{Decimal(amount) - Decimal('20.00'):.2f}")

    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"[4/5] 账单样例 -> {target.relative_to(ROOT)}（{len(lines) - 1} 行，含 1 笔渠道多账、1 笔金额差异）")
    return target


def report(conn):
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT ROUND(SUM(CASE WHEN status <> 'CLOSED' THEN amount ELSE 0 END), 2)                    AS receivable,
                   ROUND(SUM(CASE WHEN status IN ('PAID', 'PARTIAL_REFUND') THEN paid_amount ELSE 0 END), 2) AS received,
                   COUNT(*),
                   SUM(CASE WHEN status IN ('PAID', 'PARTIAL_REFUND') THEN 1 ELSE 0 END),
                   COUNT(DISTINCT student_id)
            FROM payment_order WHERE deleted = 0
            """
        )
        receivable, received, orders, paid_orders, students = cur.fetchone()

        counts = {}
        for table in ["sys_user", "student", "payment_item", "payment_order",
                      "refund_record", "reconciliation_batch", "reconciliation_detail", "notice"]:
            cur.execute(f"SELECT COUNT(*) FROM {table}")
            counts[table] = cur.fetchone()[0]

    receivable = Decimal(receivable or 0)
    received = Decimal(received or 0)
    outstanding = max(receivable - received, Decimal("0"))
    rate = (received * 100 / receivable).quantize(Decimal("0.01")) if receivable else Decimal("0.00")

    print("[5/5] 数据就绪")
    print(f"      账号 {counts['sys_user']} 个 | 学籍 {counts['student']} 条 | 项目 {counts['payment_item']} 个 | "
          f"订单 {counts['payment_order']} 笔 | 退款 {counts['refund_record']} 条 | "
          f"对账批次 {counts['reconciliation_batch']} / 明细 {counts['reconciliation_detail']} | 通知 {counts['notice']} 条")
    print(f"      应收 {receivable} 元 | 实收 {received} 元 | 欠费 {outstanding} 元 | 缴费率 {rate}% | "
          f"已付 {paid_orders}/{orders} 笔 | 涉及学生 {students} 人")


def main():
    parser = argparse.ArgumentParser(description="重置开发库并灌入演示数据")
    parser.add_argument("--no-backup", action="store_true", help="跳过备份")
    parser.add_argument("--data-only", action="store_true", help="不重建表，仅清空业务数据后重灌")
    args = parser.parse_args()

    try:
        import pymysql
    except ImportError:
        sys.exit("缺少依赖：pip install pymysql")

    cfg = read_db_config()
    print(f"目标库 {cfg['user']}@{cfg['host']}:{cfg['port']}/{cfg['database']}")

    if not args.no_backup:
        backup(cfg)
    else:
        print("[1/5] 已跳过备份")

    # schema.sql 自带 DROP DATABASE，连接时不能指定 database
    conn = pymysql.connect(
        host=cfg["host"], port=cfg["port"], user=cfg["user"], password=cfg["password"],
        charset="utf8mb4", autocommit=True,
    )
    try:
        if args.data_only:
            print("[2/5] 保留表结构，仅清空业务数据")
            conn.select_db(cfg["database"])
            truncate_business_tables(conn)
        else:
            print("[2/5] 执行 schema.sql（重建库表）")
            run_sql_file(conn, RES / "db" / "schema.sql")

        conn.select_db(cfg["database"])
        print("[3/5] 执行 data.sql + demo-data.sql")
        run_sql_file(conn, RES / "db" / "data.sql")
        run_sql_file(conn, RES / "db" / "demo-data.sql")

        build_sample_bill(conn)
        report(conn)
    finally:
        conn.close()

    print("\n完成。启动后端后即可用以下账号登录（密码均为 123456）：")
    print("  管理员 admin / 财务 caiwu01 / 学生 20230101（李小明，学费已缴清）")
    print("  学生 20230102（王小红，有部分退款） / 20240102（林晓彤，学费待支付） / 20240201（黄子轩，无待缴订单）")


if __name__ == "__main__":
    main()
