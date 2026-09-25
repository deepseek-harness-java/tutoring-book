package cn.xiaofuge.q.plugin;

import cn.xiaofuge.deepseek.harness.domain.model.entity.AbstractTool;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.xiaofuge.deepseek.harness.domain.spi.AbstractHarnessPlugin;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginContext;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginHookResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** AI 家教预约管家插件：把 tutoring-book REST API 注册为 DSH Agent 工具 */
public class TutorPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "tutor-copilot";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public TutorPlugin() { super(PLUGIN_ID); }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new CourseListTool(),
                new TeacherListTool(),
                new BookTool(),
                new OrderInfoTool(),
                new StatsTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("tutor-capabilities", 20, """
                ## AI 家教预约管家（一对一辅导机构 · 2026-09-25）
                - 查课程 → course_list（5 门课程单价与时长：小学全科150/初中数理化220/高中数学300/高中英语280/
                  少儿编程260；10 课时起 9 折，试课 49 元）
                - 查教师 → teacher_list（3 位老师特长/评分/在单量）
                - 购课排课 → book（customer/phone/student/course/startDate/sessions 必填，teacherId 可选默认林老师 T01；
                  必须先复述学生、课程、单价、课时、总价、首次课时间请家长确认后才能调用；成功报单号）
                - 订单查询 → order_info（orderId：B6001 格式；学生/课程/老师/课时/金额/状态）
                - 问运营 → stats（总单量/进行中/已结课/待上课时/营收与预计营收/分课程分老师分布/营销建议）
                - 回答要求：
                  1) 购课前必须复述要素（学生/课程/单价/课时/总价/首次课时间）请家长确认
                  2) 购课结果必报单号与首次课时间
                  3) 提醒：首次课前有免费学情测评；老师会在课后反馈学习情况；缺课需提前 2 小时请假可补
                  4) 价格与优惠只转述工具返回，禁止编造折扣
                """);
        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__" + PLUGIN_ID + "__")) {
                return PluginHookResult.context("audit: tutor tool call.");
            }
            return null;
        });
    }

    private String get(String path, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path)).GET().build());
    }

    private String post(String path, String jsonBody, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build());
    }

    private String baseUrl(Map<String, Object> args) {
        Object override = args == null ? null : args.get("appBaseUrl");
        return override == null || String.valueOf(override).isBlank()
                ? System.getenv().getOrDefault("TUTOR_APP_BASE_URL", "http://127.0.0.1:18115")
                : String.valueOf(override);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return "{\"error\":true,\"status\":" + resp.statusCode() + "}";
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private String json(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private class CourseListTool extends AbstractTool {
        @Override public String name() { return "course_list"; }
        @Override public String description() {
            return "辅导课程价目表：5 门课程的单价与时长（小学/初中/高中/少儿编程），含优惠规则与试课价。"
                    + "报价、购课前必查。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/courses", args));
        }
    }

    private class TeacherListTool extends AbstractTool {
        @Override public String name() { return "teacher_list"; }
        @Override public String description() {
            return "教师列表：姓名/特长/评分/在单量。家长挑老师、问谁教得好时调用。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/teachers", args));
        }
    }

    private class BookTool extends AbstractTool {
        @Override public String name() { return "book"; }
        @Override public String description() {
            return "购课排课：customer（家长）/phone（联系电话）/student（学生姓名+年级）/course（课程）/startDate（首次课时间）/sessions（课时数）必填，"
                    + "teacherId（老师 T01-T03）可选默认 T01。必须先复述学生、课程、单价、课时、总价、时间经家长确认后才能调用。"
                    + "成功返回单号。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("customer", stringSchema("家长姓名"))
                    .prop("phone", stringSchema("联系电话"))
                    .prop("student", stringSchema("学生姓名+年级，如：小薄（初二）"))
                    .prop("course", stringSchema("课程：小学全科 / 初中数理化 / 高中数学 / 高中英语 / 少儿编程"))
                    .prop("teacherId", stringSchema("老师编号 T01-T03，可选，默认 T01 林老师"))
                    .prop("startDate", stringSchema("首次上课时间，如：周五 19:00"))
                    .prop("sessions", stringSchema("购买课时数，正整数"))
                    .required("customer", "phone", "student", "course", "startDate", "sessions")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String body = "{\"customer\":\"" + json(str(args, "customer"))
                    + "\",\"phone\":\"" + json(str(args, "phone"))
                    + "\",\"student\":\"" + json(str(args, "student"))
                    + "\",\"course\":\"" + json(str(args, "course"))
                    + "\",\"teacherId\":\"" + json(str(args, "teacherId"))
                    + "\",\"startDate\":\"" + json(str(args, "startDate"))
                    + "\",\"sessions\":\"" + json(str(args, "sessions")) + "\"}";
            return ok(post("/api/book", body, args));
        }
    }

    private class OrderInfoTool extends AbstractTool {
        @Override public String name() { return "order_info"; }
        @Override public String description() {
            return "订单查询：orderId 必填（B6001 格式）。返回学生/课程/老师/课时/金额/状态（已排课、授课中、已结课）。"
                    + "何时必须调用：家长问订单、问剩余课时。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("orderId", stringSchema("订单号，如 B6001"))
                    .required("orderId")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/order?orderId=" + java.net.URLEncoder.encode(str(args, "orderId"), StandardCharsets.UTF_8), args));
        }
    }

    private class StatsTool extends AbstractTool {
        @Override public String name() { return "stats"; }
        @Override public String description() {
            return "运营统计：总单量/进行中/已结课/待上课时/营收与预计营收/分课程分老师分布/营销建议。"
                    + "何时必须调用：问今天运营、问单量与营收。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stats", args));
        }
    }
}
