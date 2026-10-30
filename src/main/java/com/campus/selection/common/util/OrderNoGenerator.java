package com.campus.selection.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final char[] BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private OrderNoGenerator() {
    }

    public static String next() {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(BASE36[ThreadLocalRandom.current().nextInt(BASE36.length)]);
        }
        return "CS" + LocalDateTime.now().format(FORMATTER) + suffix;
    }
}
