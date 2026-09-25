package cn.xiaofuge.q.app;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 家教预约管家 REST 接口。
 * 提供：课程价目 / 教师列表 / 购课排课 / 订单查询 / 运营统计。
 */
@RestController
@RequestMapping("/api")
public class QController {

    private final QStore store;

    public QController(QStore store) {
        this.store = store;
    }

    /** 课程价目表 */
    @GetMapping("/courses")
    public Map<String, Object> courses() {
        return store.courseList();
    }

    /** 教师列表 */
    @GetMapping("/teachers")
    public Map<String, Object> teachers() {
        return store.teacherList();
    }

    /** 购课排课 */
    @PostMapping("/book")
    public Map<String, Object> book(@RequestBody Map<String, Object> body) {
        Integer sessions = null;
        Object d = body.get("sessions");
        if (d instanceof Number n) sessions = n.intValue();
        else if (d != null) {
            try { sessions = Integer.valueOf(String.valueOf(d).trim()); } catch (NumberFormatException ignored) { }
        }
        return store.book(str(body, "customer"), str(body, "phone"), str(body, "student"),
                str(body, "course"), str(body, "teacherId"), str(body, "startDate"), sessions);
    }

    private String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    /** 订单查询 */
    @GetMapping("/order")
    public Map<String, Object> orderInfo(@RequestParam(required = false) String orderId) {
        return store.orderInfo(orderId == null ? "" : orderId);
    }

    /** 运营统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return store.stats();
    }
}
