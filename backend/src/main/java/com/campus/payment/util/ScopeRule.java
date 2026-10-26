package com.campus.payment.util;

import com.campus.payment.entity.Student;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 适用范围规则。
 * <p>
 * 四个维度（年级 / 学院 / 专业 / 指定学生）可任意组合，非空维度之间取<b>交集</b>，
 * 四维全空表示全校学生。历史数据（单一维度的 {@code target_scope} + JSON 数组）
 * 在 {@link #of} 中统一翻译成本结构，因此匹配与展示只有一套逻辑。
 */
public final class ScopeRule {

    /** 兼容标记：老数据里「维度已指定但值为空」，按原语义视为谁都匹配不上 */
    private static final ScopeRule NONE = new ScopeRule(List.of(), List.of(), List.of(), List.of(), true);

    private final List<String> grades;
    private final List<String> colleges;
    private final List<String> majors;
    private final List<String> studentIds;
    private final boolean matchNone;

    private ScopeRule(List<String> grades, List<String> colleges, List<String> majors,
                      List<String> studentIds, boolean matchNone) {
        this.grades = grades;
        this.colleges = colleges;
        this.majors = majors;
        this.studentIds = studentIds;
        this.matchNone = matchNone;
    }

    public static ScopeRule empty() {
        return new ScopeRule(List.of(), List.of(), List.of(), List.of(), false);
    }

    /** 老数据语义：指定了维度却没有值 → 不匹配任何学生 */
    public static ScopeRule none() {
        return NONE;
    }

    public static ScopeRule of(List<String> grades, List<String> colleges, List<String> majors,
                               List<String> studentIds) {
        List<String> g = clean(grades);
        List<String> c = clean(colleges);
        List<String> m = clean(majors);
        List<String> s = clean(studentIds);
        if (g.isEmpty() && c.isEmpty() && m.isEmpty() && s.isEmpty()) {
            return empty();
        }
        return new ScopeRule(g, c, m, s, false);
    }

    /**
     * 把库里的 {@code target_scope + scope_value} 翻译成统一规则。
     * scope_value 既可能是老格式的数组，也可能是 COMPOSITE 的对象。
     */
    public static ScopeRule of(String targetScope, String scopeValueJson, ObjectMapper objectMapper) {
        if (targetScope == null || targetScope.isBlank() || "ALL".equalsIgnoreCase(targetScope)) {
            return empty();
        }
        if (scopeValueJson == null || scopeValueJson.isBlank()) {
            return none();
        }
        try {
            JsonNode node = objectMapper.readTree(scopeValueJson);
            if (node.isObject()) {
                ScopeRule rule = of(texts(node.get("grades")), texts(node.get("colleges")),
                        texts(node.get("majors")), texts(node.get("studentIds")));
                return rule.isEmpty() ? none() : rule;
            }
            List<String> values = texts(node);
            if (values.isEmpty()) {
                return none();
            }
            return switch (targetScope.toUpperCase()) {
                case "GRADE" -> of(values, null, null, null);
                case "COLLEGE" -> of(null, values, null, null);
                case "MAJOR" -> of(null, null, values, null);
                case "CUSTOM" -> of(null, null, null, values);
                default -> empty();
            };
        } catch (Exception e) {
            return none();
        }
    }

    public boolean isEmpty() {
        return grades.isEmpty() && colleges.isEmpty() && majors.isEmpty() && studentIds.isEmpty();
    }

    /** 学生是否命中：非空维度全部满足才算命中 */
    public boolean matches(Student student) {
        if (student == null || matchNone) {
            return false;
        }
        if (isEmpty()) {
            return true;
        }
        return contains(grades, String.valueOf(student.getGrade()))
                && contains(colleges, student.getCollege())
                && contains(majors, student.getMajor())
                && contains(studentIds, String.valueOf(student.getId()));
    }

    /** 序列化为 COMPOSITE 的 scope_value，只写非空维度 */
    public String toJson(ObjectMapper objectMapper) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!grades.isEmpty()) {
            map.put("grades", grades);
        }
        if (!colleges.isEmpty()) {
            map.put("colleges", colleges);
        }
        if (!majors.isEmpty()) {
            map.put("majors", majors);
        }
        if (!studentIds.isEmpty()) {
            map.put("studentIds", studentIds);
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 供列表 / 标签展示的一句话描述 */
    public String text() {
        if (matchNone) {
            return "无匹配范围";
        }
        List<String> parts = new ArrayList<>();
        if (!grades.isEmpty()) {
            parts.add(grades.stream().map(g -> g + " 级").reduce((a, b) -> a + "、" + b).orElse(""));
        }
        if (!colleges.isEmpty()) {
            parts.add(String.join("、", colleges));
        }
        if (!majors.isEmpty()) {
            parts.add(String.join("、", majors));
        }
        if (!studentIds.isEmpty()) {
            parts.add("指定 " + studentIds.size() + " 名学生");
        }
        return parts.isEmpty() ? "全校" : String.join(" · ", parts);
    }

    public List<String> getGrades() {
        return grades;
    }

    public List<String> getColleges() {
        return colleges;
    }

    public List<String> getMajors() {
        return majors;
    }

    public List<String> getStudentIds() {
        return studentIds;
    }

    private static boolean contains(List<String> values, String target) {
        if (values.isEmpty()) {
            return true;
        }
        return target != null && values.stream().anyMatch(v -> target.equalsIgnoreCase(v.trim()));
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                set.add(v.trim());
            }
        }
        return List.copyOf(set);
    }

    private static List<String> texts(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            if (item != null && !item.isNull()) {
                values.add(item.asText());
            }
        }
        return values;
    }
}
