package com.campus.selection.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.selection.auth.dto.LoginRequest;
import com.campus.selection.auth.dto.LoginResponse;
import com.campus.selection.auth.dto.UserProfile;
import com.campus.selection.auth.entity.Student;
import com.campus.selection.auth.mapper.StudentMapper;
import com.campus.selection.common.exception.BusinessException;
import com.campus.selection.common.exception.ErrorCode;
import com.campus.selection.common.security.JwtTokenProvider;
import com.campus.selection.common.security.LoginUser;
import com.campus.selection.domain.AccountStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "学号或密码错误";

    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Student student = studentMapper.selectOne(Wrappers.<Student>lambdaQuery()
                .eq(Student::getStudentNo, request.studentNo().trim()));

        if (student == null) {
            // 对不存在的账号也执行一次 BCrypt，降低通过响应时间枚举账号的风险。
            passwordEncoder.matches(request.password(), "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        if (student.getStatus() != AccountStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), student.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
        }

        studentMapper.update(null, Wrappers.<Student>lambdaUpdate()
                .eq(Student::getId, student.getId())
                .set(Student::getLastLoginAt, LocalDateTime.now()));
        return issueTokens(student);
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "refreshToken 已失效，请重新登录");
        }
        Long studentId = Long.valueOf(String.valueOf(claims.get("uid")));
        Student student = studentMapper.selectById(studentId);
        if (student == null || student.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号不可用，请重新登录");
        }
        return issueTokens(student);
    }

    @Transactional(readOnly = true)
    public UserProfile me(LoginUser loginUser) {
        Student student = studentMapper.selectById(loginUser.id());
        if (student == null || student.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号不可用，请重新登录");
        }
        return UserProfile.from(student);
    }

    private LoginResponse issueTokens(Student student) {
        LoginUser loginUser = new LoginUser(
                student.getId(),
                student.getStudentNo(),
                student.getName(),
                student.getRole()
        );
        if (Objects.isNull(loginUser.role())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号角色异常");
        }
        return new LoginResponse(
                tokenProvider.createAccessToken(loginUser),
                tokenProvider.createRefreshToken(loginUser),
                "Bearer",
                tokenProvider.accessTokenTtlSeconds(),
                UserProfile.from(student)
        );
    }
}
