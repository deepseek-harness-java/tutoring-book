# tutoring-book — AI 家教预约管家（一对一辅导机构）

> DSH（deepseek-harness-java）Java Native Plugin 场景案例 P76。
> 一对一辅导机构演示应用：课程价目、教师查询、购课排课、订单查询、运营统计，全部能力通过 **Java Native 插件**注册为 DSH Agent 工具，前端 AI 管家经 SSE 实时问答。

## 需求与场景

一对一辅导机构需要一个「AI 家教预约管家」：

- 家长问课程价格、老师专长，AI 直接查价目与教师表回答；
- 购课排课前 AI 必须复述要素（学生/课程/单价/课时/总价/首次课时间）请家长确认，确认后落单并回报单号；
- 家长随时查订单状态（已排课/授课中/已结课）；
- 店长问运营，AI 汇报总单量、待上课时、营收与预计营收、分课程分老师分布，并给营销建议。

## 运行地址（演示）

| 服务 | 地址 | 说明 |
|---|---|---|
| 辅导机构前端 | http://127.0.0.1:18115 | 运营看板 + 课程 + 订单 + AI 管家 |
| DSH 平台 | http://127.0.0.1:8090 | Agent 编排与插件运行时 |

## 插件信息

| 项 | 值 |
|---|---|
| pluginId | `tutor-copilot` |
| 名称 | AI 家教预约管家 |
| runtimeType | `JAVA_NATIVE`（进程内加载） |
| 入口类 | `cn.xiaofuge.q.plugin.TutorPlugin` |
| 基类 | `AbstractHarnessPlugin` + 5 个 `AbstractTool` |

## 工具清单（5 个）

| 工具 | 说明 | 对应 REST |
|---|---|---|
| `plugin__tutor-copilot__course_list` | 辅导课程价目表（5 门课程，含 9 折规则与试课价） | GET /api/courses |
| `plugin__tutor-copilot__teacher_list` | 教师列表（姓名/特长/评分/在单量） | GET /api/teachers |
| `plugin__tutor-copilot__book` | 购课排课（7 参数，先复述确认再调用） | POST /api/book |
| `plugin__tutor-copilot__order_info` | 订单查询（学生/课程/老师/课时/金额/状态） | GET /api/order |
| `plugin__tutor-copilot__stats` | 运营统计（单量/营收/分布/营销建议） | GET /api/stats |

## 预置数据

- **5 门课程**：小学全科 ¥150 / 初中数理化 ¥220 / 高中数学 ¥300 / 高中英语 ¥280 / 少儿编程 ¥260（元/课时，90–120 分钟）
- **3 位教师**：林老师 T01（初中数学/物理，4.9）、苏老师 T02（高中数学竞赛，5.0）、顾老师 T03（少儿编程/信息学，4.8）
- **3 笔种子订单**：B6001 薄先生（初中数理化·已排课）、B6002 臧女士（高中数学·授课中）、B6003 历先生（少儿编程·已结课）

## 业务规则

- 一次购 **10 课时起享 9 折**，首次试课 49 元；
- 购课必填：家长/电话/学生（姓名+年级）/课程/首次课时间/课时数；teacherId 可选，默认 T01；
- 订单状态流：已排课 → 授课中 → 已结课；营收只计已结课，预计营收计未结课；
- 红线：首次课前免费学情测评；老师课后反馈学习情况；缺课提前 2 小时请假可补；价格与优惠只转述工具返回，禁止编造折扣。

## 体验流程

1. 打开 http://127.0.0.1:18115 —— 运营看板（总单量/进行中/已结课/待上课时）、5 门课程卡片、最新订单；
2. 右侧 AI 管家逐条试：
   - 「有哪些辅导课程？高中数学多少钱一课时？」→ `course_list`
   - 「有哪些老师？擅长什么？」→ `teacher_list`
   - 「帮孩子购课：高中数学，苏老师（T02），小钟（高一），钟女士 13800666666，周五 19:00，12 课时」→ 先复述确认 → `book` 报单号与 9 折
   - 「查一下订单 B6001」→ `order_info`
   - 「今天运营情况怎么样？」→ `stats`
3. DSH 控制台话术：`激活 tutor-copilot 插件后，问课程价格或直接下单。`

## 构建与启动

```bash
# 1. 构建（JDK 17）
cd tutoring-book && mvn clean package

# 2. 启动应用（端口 18115）
SERVER_PORT=18115 java -jar p-app/target/p-app-1.0.0-SNAPSHOT.jar

# 3. 安装插件到 DSH（先拷 jar 再装再激活）
cp p-plugin/target/p-plugin-1.0.0-SNAPSHOT.jar ~/.dsh/standalone/plugins/tutor-copilot.jar
curl -X POST http://127.0.0.1:8090/api/harness/plugins/install -H 'Content-Type: application/json' \
  -d '{"pluginId":"tutor-copilot","displayName":"AI 家教预约管家","pluginVersion":"1.0.0","runtimeType":"JAVA_NATIVE","sourcePath":"'"$HOME"'/.dsh/standalone/plugins/tutor-copilot.jar","entrypoint":"cn.xiaofuge.q.plugin.TutorPlugin"}'
curl -X POST http://127.0.0.1:8090/api/harness/plugins/activate -H 'Content-Type: application/json' -d '{"pluginId":"tutor-copilot"}'

# 4. DSH standalone（如未启动）
cd ~/.dsh/standalone && java -Dspring.profiles.active=standalone -Dserver.port=8090 \
  -jar ~/.dsh/skills/dsh-java-plugin-skills/runtime/deepseek-harness-java-app.jar
```

## 工程结构

```
tutoring-book/
├── pom.xml                # 聚合工程 tutoring-book
├── p-app/                 # Spring Boot 应用（18115）
│   └── src/main/java/cn/xiaofuge/q/app/
│       ├── TutorApplication.java
│       ├── QStore.java        # 课程/教师/订单内存数据中心
│       ├── QController.java   # REST 5 端点（sessions 数值双格式容错）
│       └── AssistantController.java  # /api/assistant/stream SSE 透传 DSH
└── p-plugin/              # Java Native 插件
    └── src/main/
        ├── java/cn/xiaofuge/q/plugin/TutorPlugin.java  # 5 工具 + 系统提示词 + PRE_TOOL_USE hook
        └── resources/META-INF/
            ├── plugin.yaml
            └── services/cn.xiaofuge.deepseek.harness.domain.spi.JavaHarnessPlugin
```

## 坑位记录

- **数值参数双格式容错**：LLM 可能传 `"12"`（字符串）或 `12`（数字），Controller 用 `instanceof Number` 优先、否则 `Integer.valueOf(String)` 兜底；
- **流被强断会卡 RUNNING**：SSE 请求中途 kill curl 会让 Agent 永久卡「正在执行上一条消息」，无 stop 端点，只能重启 DSH——自动化脚本不要强杀流式请求；
- **插件激活端点是全局的**：`POST /api/harness/plugins/activate`（body 带 pluginId），不是 `/plugins/{id}/activate`；
- **SSE 透传超时**：`spring.mvc.async.request-timeout: 180s`，上游 HttpClient 超时 170s，避免长问答被切。

## 端到端验证记录（2026-09-25）

| # | 问题 | 触发工具 | 结果 |
|---|---|---|---|
| T1 | 有哪些辅导课程？高中数学多少钱？ | course_list | ✅ 5 门课程 + 300 元/课时 + 9 折说明 |
| T2 | 有哪些老师？擅长什么？ | teacher_list | ✅ 3 位老师特长/评分 |
| T3 | 购课：高中数学 苏老师 T02 12 课时 | book | ✅ B6004，¥3240（9 折），sessions="12" 字符串容错生效 |
| T4 | 查订单 B6001 | order_info | ✅ 小薄（初二）初中数理化 林老师 10 课时 已排课 |
| T5 | 运营情况 | stats | ✅ 总单量 4 / 进行中 3 / 待上课时 34 / 预计营收 ¥9040 |
