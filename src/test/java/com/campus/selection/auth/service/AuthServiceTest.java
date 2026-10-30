package com.campus.selection.auth.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campus.selection.auth.dto.LoginRequest;
import com.campus.selection.auth.entity.Student;
import com.campus.selection.auth.mapper.StudentMapper;
import com.campus.selection.common.exception.BusinessException;
import com.campus.selection.common.exception.ErrorCode;
import com.campus.selection.common.security.JwtTokenProvider;
import com.campus.selection.domain.AccountStatus;
import com.campus.selection.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(studentMapper, passwordEncoder, tokenProvider);
    }

    @Test
    void unknownAccountUsesSameCredentialError() {
        when(studentMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("20269999", "wrong-password")));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode());
        assertEquals("学号或密码错误", exception.getMessage());
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void disabledAccountCannotLogin() {
        Student student = new Student();
        student.setId(1L);
        student.setStudentNo("20260001");
        student.setPasswordHash("hash");
        student.setStatus(AccountStatus.DISABLED);
        student.setRole(Role.STUDENT);
        when(studentMapper.selectOne(any(Wrapper.class))).thenReturn(student);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("20260001", "Student@123")));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode());
        verifyNoInteractions(tokenProvider);
    }
}
