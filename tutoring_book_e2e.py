#!/usr/bin/env python3
"""tutoring-book E2E：通过业务应用 SSE 代理调用 DSH Agent，验证工具全链路。"""
import json, subprocess, sys

AGENT = "tutor-copilot"
URL = "http://127.0.0.1:18115/api/assistant/stream"

CASES = [
    ("T1 课程查询", "辅导机构有哪些课程？多少钱一课时？简洁回答", ["小学全科", "少儿编程"]),
    ("T2 老师查询", "辅导机构有哪些老师？分别教什么？简洁回答", ["林老师", "苏老师"]),
    ("T3 预约试课", "我是家长郑女士，电话13500008888，想给初一的孩子预约一节初中数理化试课，直接帮我预约，告诉我预约信息", ["预约成功", "林老师"]),
    ("T4 课程报名", "帮孩子报名少儿编程 10 课时，我是会员 u01，直接报名告诉我订单信息", ["少儿编程", "报名"]),
    ("T5 学习统计", "机构最近的报名数据怎么样？简洁回答", ["总单量", "营收"]),
]

def ask(message, timeout=170):
    payload = json.dumps({"message": message}, ensure_ascii=False)
    try:
        out = subprocess.run(
            ["curl", "-s", "--noproxy", "*", "-N", "-X", "POST", URL,
             "-H", "Content-Type: application/json", "-d", payload,
             "--max-time", str(timeout)],
            capture_output=True, text=True, timeout=timeout + 10).stdout
    except Exception as e:
        return "", f"curl 异常: {e}"
    text = []
    ev = ""
    for line in out.splitlines():
        line = line.rstrip("\r")
        if line.startswith("event:"):
            ev = line[6:].strip()
        elif line.startswith("data:"):
            s = line[5:].strip()
            if not s or s == "[DONE]" or ev != "chunk":
                continue
            try:
                j = json.loads(s)
                c = j.get("content", "")
                if c:
                    text.append(c)
            except Exception:
                pass
            ev = ""
    return "".join(text), out

def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    cases = CASES if not only else [c for c in CASES if c[0].startswith(only)]
    passed, failed = 0, []
    for name, q, keys in cases:
        reply, raw = ask(q)
        ok = all(k in reply for k in keys)
        print(f"[{'PASS' if ok else 'FAIL'}] {name}\n  Q: {q}\n  A: {reply[:200]}")
        if ok:
            passed += 1
        else:
            failed.append(name)
            if not reply:
                print(f"  raw 首行: {raw.splitlines()[:3] if raw else '(空)'}")
    print(f"\n===== tutoring-book E2E: {passed}/{len(cases)} PASS =====")

if __name__ == "__main__":
    main()
