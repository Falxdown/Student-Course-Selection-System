package com.campus.selection.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderNoGeneratorTest {

    @Test
    void orderNumbersAreUniqueAndReadable() {
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String orderNo = OrderNoGenerator.next();
            assertTrue(orderNo.startsWith("CS"));
            assertEquals(25, orderNo.length());
            numbers.add(orderNo);
        }
        assertEquals(10_000, numbers.size());
    }
}
