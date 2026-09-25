package cn.xiaofuge.q.app;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/** 家教预约数据中心：课程/教师/订单/统计 */
@Component
public class QStore {

    /** 课程：科目/单价(元/课时)/时长(分钟)/说明 */
    static final Map<String, Object[]> COURSES = new LinkedHashMap<>();
    static {
        COURSES.put("小学全科", new Object[]{150.0, 90, "语数英巩固，习惯养成"});
        COURSES.put("初中数理化", new Object[]{220.0, 90, "中考冲刺，专题突破"});
        COURSES.put("高中数学", new Object[]{300.0, 120, "高考一轮二轮，压轴题专项"});
        COURSES.put("高中英语", new Object[]{280.0, 120, "语法体系+阅读写作提分"});
        COURSES.put("少儿编程", new Object[]{260.0, 90, "Python/Scratch，项目制教学"});
    }

    /** 教师：编号/姓名/特长/评分 */
    static final Map<String, Object[]> TEACHERS = new LinkedHashMap<>();
    static {
        TEACHERS.put("T01", new Object[]{"林老师", "初中数学/物理", 4.9});
        TEACHERS.put("T02", new Object[]{"苏老师", "高中数学竞赛", 5.0});
        TEACHERS.put("T03", new Object[]{"顾老师", "少儿编程/信息学", 4.8});
    }

    public static class Order {
        public String id; public String customer; public String phone;
        public String student; public String course; public String teacher;
        public String startDate; public int sessions;
        public double total; public String status; // 已排课 / 授课中 / 已结课
    }

    public final List<Order> orders = new ArrayList<>();
    private int orderSeq = 6001;

    public QStore() { seed(); }

    private void seed() {
        orders.add(o("薄先生", "13800777777", "小薄（初二）", "初中数理化", "T01", "周四 19:00", 10, "已排课"));
        orders.add(o("臧女士", "13800888888", "小臧（高一）", "高中数学", "T02", "周四 18:00", 12, "授课中"));
        orders.add(o("历先生", "13800999999", "小历（五年级）", "少儿编程", "T03", "周三 17:00", 8, "已结课"));
    }

    private Order o(String customer, String phone, String student, String course, String teacherId, String startDate, int sessions, String status) {
        Order x = new Order(); x.id = "B" + orderSeq++; x.customer = customer; x.phone = phone;
        x.student = student; x.course = course; x.teacher = String.valueOf(TEACHERS.get(teacherId)[0]);
        x.startDate = startDate; x.sessions = sessions;
        Object[] p = COURSES.get(course);
        x.total = p != null ? (Double) p[0] * sessions : 0;
        x.status = status; return x;
    }

    /** 课程价目表 */
    public Map<String, Object> courseList() {
        List<Map<String, Object>> list = new ArrayList<>();
        COURSES.forEach((k, v) -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("course", k); m.put("pricePerSession", v[0]); m.put("minutes", v[1]); m.put("desc", v[2]); list.add(m); });
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", list.size()); r.put("courses", list);
        r.put("note", "一次购 10 课时起享 9 折，首次试课 49 元");
        return r;
    }

    /** 教师列表 */
    public Map<String, Object> teacherList() {
        List<Map<String, Object>> list = TEACHERS.entrySet().stream()
                .map(e -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", e.getKey()); m.put("name", e.getValue()[0]);
                    m.put("skill", e.getValue()[1]); m.put("rating", e.getValue()[2]);
                    m.put("activeOrders", orders.stream().filter(o -> o.teacher.equals(e.getValue()[0])
                            && !"已结课".equals(o.status)).count());
                    return m; })
                .collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("teachers", list);
        return r;
    }

    /** 购课排课 */
    public synchronized Map<String, Object> book(String customer, String phone, String student, String course, String teacherId, String startDate, Integer sessions) {
        if (customer == null || customer.isBlank())
            return Map.of("ok", false, "msg", "请提供家长姓名");
        Object[] p = COURSES.get(course);
        if (p == null) return Map.of("ok", false, "msg", "课程 " + course + " 不在价目表，可选：" + String.join("/", COURSES.keySet()));
        if (phone == null || phone.isBlank())
            return Map.of("ok", false, "msg", "请提供联系电话，方便老师沟通学情");
        if (student == null || student.isBlank())
            return Map.of("ok", false, "msg", "请提供学生信息（姓名+年级）");
        if (startDate == null || startDate.isBlank())
            return Map.of("ok", false, "msg", "请提供首次上课时间（如：周五 19:00）");
        if (sessions == null || sessions < 1)
            return Map.of("ok", false, "msg", "请提供购买课时数（至少 1 课时）");
        String tName;
        if (teacherId == null || teacherId.isBlank()) {
            tName = String.valueOf(TEACHERS.get("T01")[0]); // 默认林老师
        } else {
            var tEntry = TEACHERS.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(teacherId)).findFirst().orElse(null);
            if (tEntry == null) return Map.of("ok", false, "msg", "老师 " + teacherId + " 不存在，可选：" + String.join("/", TEACHERS.keySet()));
            tName = String.valueOf(tEntry.getValue()[0]);
        }
        double unit = (Double) p[0];
        double total = sessions >= 10 ? unit * sessions * 0.9 : unit * sessions;
        Order x = new Order(); x.id = "B" + orderSeq++; x.customer = customer; x.phone = phone;
        x.student = student; x.course = course; x.teacher = tName; x.startDate = startDate; x.sessions = sessions;
        x.total = total; x.status = "已排课";
        orders.add(0, x);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", customer);
        r.put("student", student); r.put("course", course); r.put("teacher", tName);
        r.put("startDate", startDate); r.put("sessions", sessions); r.put("total", total);
        if (sessions >= 10) r.put("discount", "已享 9 折（10 课时起）");
        r.put("msg", "排课成功！单号 " + x.id + "，" + student + " 报 " + course + "（¥" + unit + "/课时 × " + sessions + " = ¥" + total + "），老师 " + tName + "，首次课 " + startDate);
        return r;
    }

    /** 订单查询 */
    public Map<String, Object> orderInfo(String orderId) {
        Order x = orders.stream().filter(o -> o.id.equalsIgnoreCase(orderId)).findFirst().orElse(null);
        if (x == null) return Map.of("ok", false, "msg", "订单 " + orderId + " 不存在，当前共 " + orders.size() + " 单");
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", x.customer);
        r.put("student", x.student); r.put("course", x.course); r.put("teacher", x.teacher);
        r.put("startDate", x.startDate); r.put("sessions", x.sessions); r.put("total", x.total); r.put("status", x.status);
        if ("已结课".equals(x.status)) r.put("msg", "课程已结课，可查看学情报告并预约下一阶段");
        return r;
    }

    /** 运营统计 */
    public Map<String, Object> stats() {
        Map<String, Object> byCourse = new LinkedHashMap<String, Object>();
        for (String s : COURSES.keySet()) {
            long n = orders.stream().filter(o -> s.equals(o.course)).count();
            if (n > 0) byCourse.put(s, n + " 单");
        }
        Map<String, Object> byTeacher = new LinkedHashMap<String, Object>();
        for (var e : TEACHERS.entrySet()) {
            long n = orders.stream().filter(o -> o.teacher.equals(e.getValue()[0])).count();
            byTeacher.put(String.valueOf(e.getValue()[0]), n + " 单");
        }
        long ongoing = orders.stream().filter(o -> "已排课".equals(o.status) || "授课中".equals(o.status)).count();
        double revenue = orders.stream().filter(o -> "已结课".equals(o.status)).mapToDouble(o -> o.total).sum();
        double expected = orders.stream().filter(o -> !"已结课".equals(o.status)).mapToDouble(o -> o.total).sum();
        int plannedSessions = orders.stream().filter(o -> !"已结课".equals(o.status)).mapToInt(o -> o.sessions).sum();
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("totalOrders", orders.size());
        r.put("ongoing", ongoing);
        r.put("done", orders.stream().filter(o -> "已结课".equals(o.status)).count());
        r.put("plannedSessions", plannedSessions);
        r.put("revenue", revenue);
        r.put("expectedRevenue", expected);
        r.put("byCourse", byCourse);
        r.put("byTeacher", byTeacher);
        r.put("advice", "期中期末前 1 个月是购课高峰可推冲刺包；试课 49 元是引流抓手；学情报告月度推送可促续费");
        return r;
    }
}
