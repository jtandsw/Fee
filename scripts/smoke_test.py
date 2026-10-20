#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
冒烟测试脚本（对应 README 11.2 的 15 条用例）。

用法：
    python scripts/smoke_test.py
    BASE_URL=http://127.0.0.1:8080/api python scripts/smoke_test.py

说明：
    - 只依赖标准库，不需要额外安装 requests。
    - 需要后端已启动、数据库已按 README 第 7 章初始化。
    - 脚本会真实写入订单/项目/对账数据，建议只在开发库或临时库上执行。
"""

import json
import os
import sys
import time
import uuid
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta

BASE_URL = os.environ.get("BASE_URL", "http://127.0.0.1:8080/api")

PASSED = []
FAILED = []


# --------------------------------------------------------------------------- #
# HTTP helpers
# --------------------------------------------------------------------------- #
def _request(method, path, body=None, token=None, headers=None,
             raw_body=None, content_type=None):
    url = BASE_URL + path
    hdrs = {"Accept": "application/json"}
    if token:
        hdrs["Authorization"] = "Bearer " + token
    if headers:
        hdrs.update(headers)

    data = None
    if raw_body is not None:
        data = raw_body
        if content_type:
            hdrs["Content-Type"] = content_type
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        hdrs["Content-Type"] = "application/json; charset=utf-8"

    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read(), dict(resp.headers)
    except urllib.error.HTTPError as e:  # 401/403 等由 Security 直接返回
        return e.code, e.read(), dict(e.headers)


def call(method, path, body=None, token=None, headers=None, expect_code=0):
    status, payload, _ = _request(method, path, body=body, token=token, headers=headers)
    text = payload.decode("utf-8", "replace")
    try:
        result = json.loads(text)
    except json.JSONDecodeError:
        raise AssertionError("响应不是 JSON（HTTP %s）：%s" % (status, text[:200]))
    if expect_code is not None and result.get("code") != expect_code:
        raise AssertionError(
            "期望 code=%s，实际 code=%s message=%s"
            % (expect_code, result.get("code"), result.get("message"))
        )
    return result.get("data")


def upload(path, token, fields, file_field, filename, file_bytes):
    boundary = "----SmokeBoundary" + uuid.uuid4().hex
    parts = []
    for key, value in fields.items():
        parts.append(("--" + boundary + "\r\n"
                      'Content-Disposition: form-data; name="%s"\r\n\r\n' % key).encode("utf-8"))
        parts.append(str(value).encode("utf-8") + b"\r\n")
    parts.append(("--" + boundary + "\r\n"
                  'Content-Disposition: form-data; name="%s"; filename="%s"\r\n'
                  "Content-Type: text/csv\r\n\r\n" % (file_field, filename)).encode("utf-8"))
    parts.append(file_bytes + b"\r\n")
    parts.append(("--" + boundary + "--\r\n").encode("utf-8"))
    payload = b"".join(parts)

    status, resp, _ = _request("POST", path, token=token, raw_body=payload,
                               content_type="multipart/form-data; boundary=" + boundary)
    result = json.loads(resp.decode("utf-8", "replace"))
    if result.get("code") != 0:
        raise AssertionError("上传失败 code=%s message=%s" % (result.get("code"), result.get("message")))
    return result.get("data")


# --------------------------------------------------------------------------- #
# 用例框架
# --------------------------------------------------------------------------- #
def case(no, title):
    def deco(fn):
        def wrapper(ctx):
            try:
                detail = fn(ctx)
                PASSED.append((no, title, detail))
                print("[PASS] %-2s %-22s %s" % (no, title, detail or ""))
            except Exception as e:  # noqa: BLE001
                FAILED.append((no, title, str(e)))
                print("[FAIL] %-2s %-22s %s" % (no, title, e))
        wrapper.__no__ = no
        return wrapper
    return deco


def login(ctx, key, username, password="123456"):
    data = call("POST", "/v1/auth/login", {"username": username, "password": password})
    ctx[key] = {"token": data["accessToken"], "user": data["user"]}
    return data


# --------------------------------------------------------------------------- #
# 15 条用例
# --------------------------------------------------------------------------- #
@case(1, "健康检查")
def t01(ctx):
    status, payload, _ = _request("GET", "/actuator/health")
    body = json.loads(payload.decode("utf-8"))
    assert status == 200 and body.get("status") == "UP", "health=%s" % body
    return "status=UP"


@case(2, "学生登录")
def t02(ctx):
    data = login(ctx, "stu1", "20230101")
    assert data["user"]["role"] == "STUDENT", data["user"]["role"]
    assert data["user"]["realName"] == "李小明", data["user"]["realName"]
    assert data["accessToken"] and data["refreshToken"], "令牌缺失"
    return "realName=%s role=%s" % (data["user"]["realName"], data["user"]["role"])


@case(3, "待缴项目与缴费进度")
def t03(ctx):
    # payFilter=UNPAID 只返回未缴清的项目（学生首页「待缴项目」用的就是这个过滤）
    data = call("GET", "/v1/payment-items/available?pageNum=1&pageSize=20&payFilter=UNPAID",
                token=ctx["stu1"]["token"])
    items = data["list"]
    assert len(items) >= 1, "无待缴项目，无法继续下单用例"
    assert all(i["status"] == "PUBLISHED" for i in items), "存在非 PUBLISHED 项目"
    assert all(i["amount"] and i["endTime"] for i in items), "缺少金额或截止时间"
    assert all(i.get("payStatus") in ("UNPAID", "PARTIAL") for i in items), \
        "UNPAID 过滤返回了已缴清项目：%s" % [(i["itemName"], i.get("payStatus")) for i in items]
    assert all(i.get("remainAmount") is not None for i in items), "缺少剩余应缴金额"
    # 全部项目里必须能同时看到已缴清与未缴清，验证状态字段真实生效
    allp = call("GET", "/v1/payment-items/available?pageNum=1&pageSize=50&payFilter=ALL",
                token=ctx["stu1"]["token"])["list"]
    statuses = {i.get("payStatus") for i in allp}
    assert "PAID" in statuses, "全部项目中没有已缴清的项目，payStatus 可能未生效"
    paid_total = call("GET", "/v1/payment-items/available?pageNum=1&pageSize=50&payFilter=PAID",
                      token=ctx["stu1"]["token"])["total"]
    assert paid_total >= 1, "PAID 过滤数量为 %d" % paid_total
    ctx["item"] = items[0]
    return "待缴 %d 项 / 已缴清 %d 项，首个=%s 剩余¥%s" % (
        len(items), paid_total, ctx["item"]["itemName"], ctx["item"]["remainAmount"])


@case(4, "下单")
def t04(ctx):
    ctx["idem"] = str(uuid.uuid4())
    item_id = ctx["newItem"]["id"] if "newItem" in ctx else ctx["item"]["id"]
    data = call("POST", "/v1/orders", {"itemId": item_id, "channel": "MOCK"},
                token=ctx["stu1"]["token"], headers={"Idempotency-Key": ctx["idem"]})
    assert data["status"] == "PENDING", data["status"]
    assert data["orderNo"] and data["payUrl"], "缺少 orderNo/payUrl"
    ctx["order"] = data
    return "orderNo=%s amount=¥%s" % (data["orderNo"], data["amount"])


@case(5, "发起支付")
def t05(ctx):
    pay = call("POST", "/v1/orders/%s/pay" % ctx["order"]["orderNo"], token=ctx["stu1"]["token"])
    assert pay["channel"] == "MOCK" and pay["payUrl"], "payUrl 缺失"
    detail = call("GET", "/v1/orders/%s" % ctx["order"]["orderNo"], token=ctx["stu1"]["token"])
    assert detail["status"] == "PAYING", "支付后状态=%s" % detail["status"]
    ctx["txn"] = "MOCK" + datetime.now().strftime("%Y%m%d%H%M%S") + uuid.uuid4().hex[:6].upper()
    return "status=PAYING payUrl 就绪"


@case(6, "支付回调")
def t06(ctx):
    body = {"orderNo": ctx["order"]["orderNo"], "transactionNo": ctx["txn"],
            "amount": ctx["order"]["amount"], "status": "SUCCESS"}
    call("POST", "/v1/pay/callback/MOCK", body)
    detail = call("GET", "/v1/orders/%s" % ctx["order"]["orderNo"], token=ctx["stu1"]["token"])
    assert detail["status"] == "PAID", "回调后状态=%s" % detail["status"]
    assert detail["transactionNo"] == ctx["txn"], "渠道流水号未写入"
    assert detail["paidAt"], "paidAt 为空"
    return "status=PAID transactionNo=%s" % detail["transactionNo"]


@case(7, "回调重复幂等")
def t07(ctx):
    body = {"orderNo": ctx["order"]["orderNo"], "transactionNo": ctx["txn"],
            "amount": ctx["order"]["amount"], "status": "SUCCESS"}
    call("POST", "/v1/pay/callback/MOCK", body)
    detail = call("GET", "/v1/orders/%s" % ctx["order"]["orderNo"], token=ctx["stu1"]["token"])
    assert detail["status"] == "PAID", "重复回调后状态异常=%s" % detail["status"]
    return "重复回调静默成功，状态仍为 PAID"


@case(8, "订单记录与凭证")
def t08(ctx):
    data = call("GET", "/v1/orders?pageNum=1&pageSize=50", token=ctx["stu1"]["token"])
    hit = [o for o in data["list"] if o["orderNo"] == ctx["order"]["orderNo"]]
    assert hit, "我的订单中找不到该笔订单"
    assert hit[0]["status"] == "PAID", hit[0]["status"]
    status, payload, _ = _request("GET", "/v1/orders/%s/voucher" % ctx["order"]["orderNo"],
                                  token=ctx["stu1"]["token"])
    html = payload.decode("utf-8", "replace")
    assert status == 200 and "缴费凭证" in html, "凭证生成失败（HTTP %s）" % status
    return "订单可查，凭证 %d 字节" % len(html)


@case(9, "数据权限隔离")
def t09(ctx):
    login(ctx, "stu2", "20230102")
    data = call("GET", "/v1/orders?pageNum=1&pageSize=50", token=ctx["stu2"]["token"])
    leaked = [o for o in data["list"] if o["orderNo"] == ctx["order"]["orderNo"]]
    assert not leaked, "学生 B 看到了学生 A 的订单"
    return "学生 B 看不到学生 A 的订单（共 %d 条）" % len(data["list"])


@case(10, "财务视角与看板")
def t10(ctx):
    login(ctx, "fin", "caiwu01")
    data = call("GET", "/v1/orders?pageNum=1&pageSize=20&keyword=%s" % ctx["order"]["orderNo"],
                token=ctx["fin"]["token"])
    assert any(o["orderNo"] == ctx["order"]["orderNo"] for o in data["list"]), "财务看不到该订单"
    stat = call("GET", "/v1/stats/overview", token=ctx["fin"]["token"])
    assert float(stat["received"]) > 0, "看板实收为 0"
    ctx["stat"] = stat
    return "应付=¥%s 实收=¥%s 缴费率=%s%%" % (stat["receivable"], stat["received"], stat["payRate"])


@case(11, "项目新建与发布")
def t11(ctx):
    if "fin" not in ctx:
        login(ctx, "fin", "caiwu01")
    now = datetime.now()
    payload = {
        "itemName": "冒烟测试项目" + now.strftime("%H%M%S"),
        "category": "OTHER",
        "term": "2026 春季",
        "amount": 100.00,
        "allowPartial": 0,
        "targetScope": "ALL",
        "startTime": (now - timedelta(days=1)).strftime("%Y-%m-%d %H:%M:%S"),
        "endTime": (now + timedelta(days=7)).strftime("%Y-%m-%d %H:%M:%S"),
        "description": "冒烟测试自动创建，可删除",
    }
    item = call("POST", "/v1/payment-items", payload, token=ctx["fin"]["token"])
    assert item["status"] == "DRAFT", item["status"]
    call("POST", "/v1/payment-items/%d/publish" % item["id"], token=ctx["fin"]["token"])
    avail = call("GET", "/v1/payment-items/available?pageNum=1&pageSize=50&payFilter=UNPAID",
                 token=ctx["stu1"]["token"])
    hit = next((i for i in avail["list"] if i["id"] == item["id"]), None)
    assert hit is not None, "发布后学生端仍不可见"
    assert hit["payStatus"] == "UNPAID", "新建项目应为未缴费，实际=%s" % hit.get("payStatus")
    ctx["newItem"] = item
    return "itemNo=%s 发布后学生端可见" % item["itemNo"]


@case(12, "幂等键复用")
def t12(ctx):
    # 单独建一个项目，避免与「下单」用的项目互相影响（同一项目缴清后无法再下单）
    now = datetime.now()
    item = call("POST", "/v1/payment-items", {
        "itemName": "幂等测试项目" + now.strftime("%H%M%S"),
        "category": "OTHER",
        "term": "2026 春季",
        "amount": 66.00,
        "allowPartial": 0,
        "targetScope": "ALL",
        "startTime": (now - timedelta(days=1)).strftime("%Y-%m-%d %H:%M:%S"),
        "endTime": (now + timedelta(days=7)).strftime("%Y-%m-%d %H:%M:%S"),
        "description": "冒烟测试自动创建，可删除",
    }, token=ctx["fin"]["token"])
    call("POST", "/v1/payment-items/%d/publish" % item["id"], token=ctx["fin"]["token"])
    ctx["idemItem"] = item

    key = str(uuid.uuid4())
    body = {"itemId": item["id"], "channel": "MOCK"}
    first = call("POST", "/v1/orders", body, token=ctx["stu1"]["token"],
                 headers={"Idempotency-Key": key})
    second = call("POST", "/v1/orders", body, token=ctx["stu1"]["token"],
                  headers={"Idempotency-Key": key})
    assert first["orderNo"] == second["orderNo"], "两次返回不同订单：%s / %s" % (
        first["orderNo"], second["orderNo"])
    ctx["pendingOrder"] = first
    return "同一 Idempotency-Key 返回同一 orderNo=%s" % first["orderNo"]


@case(13, "缺幂等键拦截")
def t13(ctx):
    status, payload, _ = _request("POST", "/v1/orders",
                                  body={"itemId": ctx["idemItem"]["id"], "channel": "MOCK"},
                                  token=ctx["stu1"]["token"])
    result = json.loads(payload.decode("utf-8", "replace"))
    assert result.get("code") == 40002, "缺少 Idempotency-Key 时返回 %s" % result.get("code")
    return "未带 Idempotency-Key 返回 40002"


@case(14, "越权访问拦截")
def t14(ctx):
    result = call("GET", "/v1/users?pageNum=1&pageSize=10", token=ctx["stu1"]["token"], expect_code=40302)
    assert result is None
    return "学生访问 /v1/users 被拒（40302）"


@case(15, "关闭订单后禁止支付")
def t15(ctx):
    order_no = ctx["pendingOrder"]["orderNo"]
    call("POST", "/v1/orders/%s/close" % order_no, token=ctx["stu1"]["token"])
    detail = call("GET", "/v1/orders/%s" % order_no, token=ctx["stu1"]["token"])
    assert detail["status"] == "CLOSED", detail["status"]
    body = {"orderNo": order_no, "transactionNo": "MOCKCLOSED" + uuid.uuid4().hex[:8],
            "amount": ctx["pendingOrder"]["amount"], "status": "SUCCESS"}
    call("POST", "/v1/pay/callback/MOCK", body)
    after = call("GET", "/v1/orders/%s" % order_no, token=ctx["stu1"]["token"])
    assert after["status"] == "CLOSED", "已关闭订单被回调改成了 %s" % after["status"]
    result = call("POST", "/v1/orders/%s/pay" % order_no, token=ctx["stu1"]["token"], expect_code=60002)
    assert result is None
    return "再次支付返回 60002，回调未篡改状态"


@case(16, "退款")
def t16(ctx):
    order = ctx["order"]
    order_no = order["orderNo"]
    # 1) 部分退款 -> PARTIAL_REFUND
    part = call("POST", "/v1/orders/%s/refund" % order_no,
                {"amount": "1.00", "reason": "冒烟测试部分退款"}, token=ctx["fin"]["token"])
    assert part["status"] == "SUCCESS", part["status"]
    detail = call("GET", "/v1/orders/%s" % order_no, token=ctx["stu1"]["token"])
    assert detail["status"] == "PARTIAL_REFUND", detail["status"]
    # 2) 超出实缴 -> 60005
    over = call("POST", "/v1/orders/%s/refund" % order_no,
                {"amount": "99999.00", "reason": "超额退款"},
                token=ctx["fin"]["token"], expect_code=60005)
    assert over is None
    # 3) 退完剩余 -> REFUNDED
    rest = (float(order["amount"]) - 1.00)
    call("POST", "/v1/orders/%s/refund" % order_no,
         {"amount": "%.2f" % rest, "reason": "退完剩余"}, token=ctx["fin"]["token"])
    detail = call("GET", "/v1/orders/%s" % order_no, token=ctx["stu1"]["token"])
    assert detail["status"] == "REFUNDED", detail["status"]
    return "部分退款→PARTIAL_REFUND，超额→60005，退完→REFUNDED"


@case(17, "对账上传与匹配")
def t17(ctx):
    # 订单已被退款，重新造一笔已支付订单用于对账
    order = call("POST", "/v1/orders", {"itemId": ctx["newItem"]["id"], "channel": "MOCK"},
                 token=ctx["stu1"]["token"], headers={"Idempotency-Key": str(uuid.uuid4())})
    call("POST", "/v1/orders/%s/pay" % order["orderNo"], token=ctx["stu1"]["token"])
    txn = "MOCKRECON" + uuid.uuid4().hex[:8].upper()
    call("POST", "/v1/pay/callback/MOCK",
         {"orderNo": order["orderNo"], "transactionNo": txn, "amount": order["amount"], "status": "SUCCESS"})

    lines = [
        "订单号,渠道流水号,金额",
        "%s,%s,%s" % (order["orderNo"], txn, order["amount"]),
        "%s,%s,%s" % (order["orderNo"], "MOCKDIFF" + uuid.uuid4().hex[:6], "1.00"),
        ",MOCKONLY" + uuid.uuid4().hex[:6] + ",88.00",
    ]
    csv_bytes = ("\n".join(lines) + "\n").encode("utf-8-sig")
    # 同一渠道 + 同一账单日期只允许一个批次；重复执行脚本时换一个历史日期，
    # 此时当日已支付订单不会进入匹配范围，全部记为 CHANNEL_ONLY。
    today = date.today().isoformat()
    bill_date = today
    try:
        batch = upload("/v1/reconciliations?channel=MOCK&billDate=" + bill_date,
                       ctx["fin"]["token"], {}, "file", "mock_bill_%s.csv" % uuid.uuid4().hex[:6], csv_bytes)
    except AssertionError as e:
        if "60009" not in str(e):
            raise
        import random
        bill_date = (date(2020, 1, 1) + timedelta(days=random.randint(0, 3000))).isoformat()
        batch = upload("/v1/reconciliations?channel=MOCK&billDate=" + bill_date,
                       ctx["fin"]["token"], {}, "file", "mock_bill_%s.csv" % uuid.uuid4().hex[:6], csv_bytes)
    assert batch["status"] == "PENDING", "新建批次状态=%s" % batch["status"]
    assert batch["batchNo"], "批次号缺失"

    matched = call("POST", "/v1/reconciliations/%s/match" % batch["batchNo"], token=ctx["fin"]["token"])
    assert matched["totalCount"] == 3, "账单行数=%s" % matched["totalCount"]
    if bill_date == today:
        assert matched["matchedCount"] == 1, "匹配笔数=%s" % matched["matchedCount"]
        # 差异 = 账单侧 2 笔（金额不一致 + 渠道多单）+ 本地有账单无的 LOCAL_ONLY（历史上已支付的订单）
        assert matched["diffCount"] >= 2, "差异笔数=%s" % matched["diffCount"]
    else:
        assert matched["diffCount"] >= 3, "历史账单应全部为差异，实际=%s" % matched["diffCount"]
    details = call("GET", "/v1/reconciliations/%s/details?pageNum=1&pageSize=50" % batch["batchNo"],
                   token=ctx["fin"]["token"])
    diffs = sorted({d["diffType"] for d in details["list"]})
    assert "CHANNEL_ONLY" in diffs, "缺少 CHANNEL_ONLY，实际=%s" % diffs
    if bill_date == today:
        assert "MATCH" in diffs and "AMOUNT_DIFF" in diffs, "差异类型不全，实际=%s" % diffs
    ctx["batch"] = matched

    # 核销：把差异明细逐条处理掉，批次应自动归档为 FINISHED
    pending = [d for d in details["list"] if d["handleStatus"] == "UNHANDLED"]
    for d in pending:
        call("PUT", "/v1/reconciliations/details/%d/handle" % d["id"],
             {"handleStatus": "HANDLED", "handleRemark": "冒烟测试核销"}, token=ctx["fin"]["token"])
    final = call("GET", "/v1/reconciliations?pageNum=1&pageSize=10", token=ctx["fin"]["token"])
    row = next(b for b in final["list"] if b["batchNo"] == batch["batchNo"])
    assert row["status"] == "FINISHED", "核销后批次状态=%s" % row["status"]
    return "batchNo=%s 行数=%s 匹配=%s 差异=%s 已核销%d条" % (
        matched["batchNo"], matched["totalCount"], matched["matchedCount"],
        matched["diffCount"], len(pending))


@case(18, "统计与导出")
def t18(ctx):
    by_item = call("GET", "/v1/stats/by-item", token=ctx["fin"]["token"])
    by_college = call("GET", "/v1/stats/by-college", token=ctx["fin"]["token"])
    trend = call("GET", "/v1/stats/trend?granularity=day", token=ctx["fin"]["token"])
    assert isinstance(by_item, list) and by_item, "按项目统计为空"
    assert isinstance(by_college, list) and by_college, "按学院统计为空"
    assert isinstance(trend, list) and trend, "趋势数据为空"
    status, payload, headers = _request("GET", "/v1/stats/export", token=ctx["fin"]["token"])
    assert status == 200 and len(payload) > 1000, "导出文件异常（%s 字节）" % len(payload)
    assert payload[:2] == b"PK", "导出内容不是 xlsx"
    return "项目 %d 项 / 学院 %d 项 / 趋势 %d 点 / 导出 %d KB" % (
        len(by_item), len(by_college), len(trend), len(payload) // 1024)


@case(19, "管理员禁用账号")
def t19(ctx):
    login(ctx, "admin", "admin")
    users = call("GET", "/v1/users?pageNum=1&pageSize=50&role=STUDENT", token=ctx["admin"]["token"])
    target = next(u for u in users["list"] if u["username"] == "20230102")
    call("PUT", "/v1/users/%d/status" % target["id"], {"status": 0}, token=ctx["admin"]["token"])
    try:
        call("POST", "/v1/auth/login", {"username": "20230102", "password": "123456"}, expect_code=40104)
    finally:
        call("PUT", "/v1/users/%d/status" % target["id"], {"status": 1}, token=ctx["admin"]["token"])
    call("POST", "/v1/auth/login", {"username": "20230102", "password": "123456"})
    logs = call("GET", "/v1/logs?pageNum=1&pageSize=10", token=ctx["admin"]["token"])
    assert logs["total"] > 0, "操作日志为空"
    return "禁用后登录返回 40104，恢复后登录成功，操作日志 %d 条" % logs["total"]


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #
def main():
    print("BASE_URL = %s\n" % BASE_URL)
    start = time.time()
    ctx = {}
    # 执行顺序：先用财务账号建一个本次运行专用的项目（脚本可重复执行，不会命中「已缴清」），
    # 再按 README 11.2 的用例号 1→19 顺序校验。
    steps = [t01, t02, t11, t03, t04, t05, t06, t07, t08, t09, t10,
             t12, t13, t14, t15, t16, t17, t18, t19]
    for step in steps:
        step(ctx)

    print("\n" + "-" * 68)
    print("通过 %d 条，失败 %d 条，耗时 %.1fs" % (len(PASSED), len(FAILED), time.time() - start))
    if FAILED:
        print("\n失败明细：")
        for no, title, err in FAILED:
            print("  #%s %s -> %s" % (no, title, err))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
