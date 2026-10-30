package com.campus.selection.common.cache;

public final class CacheKeys {

    private static final String PREFIX = "ccs:";

    private CacheKeys() {
    }

    public static String stock(Long courseId) {
        return PREFIX + "course:stock:" + courseId;
    }

    public static String selectedStudents(Long courseId) {
        return PREFIX + "course:selected:" + courseId;
    }

    public static String idempotency(Long studentId, String key) {
        return PREFIX + "idem:" + studentId + ":" + key;
    }

    public static String rateLimit(String scope, String identity) {
        return PREFIX + "rate:" + scope + ":" + identity;
    }

    public static String hotCourses(int limit) {
        return PREFIX + "cache:hot:" + limit;
    }
}
