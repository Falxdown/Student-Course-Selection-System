package com.campus.selection.selection.message;

import java.io.Serializable;

public record SelectionMessage(
        String requestId,
        Long studentId,
        Long courseId,
        long createdAt
) implements Serializable {
}
