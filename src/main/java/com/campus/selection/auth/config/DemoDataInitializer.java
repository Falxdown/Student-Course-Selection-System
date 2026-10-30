package com.campus.selection.auth.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.selection.auth.entity.Student;
import com.campus.selection.auth.mapper.StudentMapper;
import com.campus.selection.domain.AccountStatus;
import com.campus.selection.domain.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent("20260001", "刘同学", "Student@123", Role.STUDENT);
        createIfAbsent("20260002", "陈同学", "Student@123", Role.STUDENT);
        createIfAbsent("20260003", "林同学", "Student@123", Role.STUDENT);
        createIfAbsent("admin", "系统管理员", "Admin@123", Role.ADMIN);
        log.warn("Demo accounts are enabled. Student: 20260001 / Student@123, Admin: admin / Admin@123. "
                + "Set DEMO_DATA_ENABLED=false in production.");
    }

    private void createIfAbsent(String studentNo, String name, String rawPassword, Role role) {
        Long count = studentMapper.selectCount(Wrappers.<Student>lambdaQuery()
                .eq(Student::getStudentNo, studentNo));
        if (count != null && count > 0) {
            return;
        }
        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName(name);
        student.setPasswordHash(passwordEncoder.encode(rawPassword));
        student.setRole(role);
        student.setStatus(AccountStatus.ACTIVE);
        try {
            studentMapper.insert(student);
        } catch (DuplicateKeyException ignored) {
            // 多实例同时启动时由唯一索引兜底，另一个实例已经创建该演示账号。
        }
    }
}
