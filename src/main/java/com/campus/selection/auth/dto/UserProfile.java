package com.campus.selection.auth.dto;

import com.campus.selection.auth.entity.Student;

public record UserProfile(Long id, String studentNo, String name, String role) {

    public static UserProfile from(Student student) {
        return new UserProfile(
                student.getId(),
                student.getStudentNo(),
                student.getName(),
                student.getRole().name()
        );
    }
}
