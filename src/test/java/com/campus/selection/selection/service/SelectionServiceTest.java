package com.campus.selection.selection.service;

import com.campus.selection.common.config.SelectionProperties;
import com.campus.selection.common.exception.BusinessException;
import com.campus.selection.common.exception.ErrorCode;
import com.campus.selection.common.security.LoginUser;
import com.campus.selection.course.mapper.CourseMapper;
import com.campus.selection.course.service.CourseService;
import com.campus.selection.domain.Role;
import com.campus.selection.selection.mapper.SelectionOrderMapper;
import com.campus.selection.selection.mapper.SelectionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectionServiceTest {

    @Mock
    private CourseService courseService;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private SelectionTaskMapper taskMapper;
    @Mock
    private SelectionOrderMapper orderMapper;
    @Mock
    private StockRedisService stockRedisService;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SelectionService selectionService;

    private final LoginUser user = new LoginUser(1L, "20260001", "刘同学", Role.STUDENT);

    @BeforeEach
    void setUp() {
        selectionService = new SelectionService(
                courseService,
                courseMapper,
                taskMapper,
                orderMapper,
                stockRedisService,
                rateLimitService,
                rabbitTemplate,
                redis,
                new SelectionProperties(10, 5, 86400, 30, 120, 100)
        );
    }

    @Test
    void rejectsMissingOrShortIdempotencyKeyBeforeTouchingRedis() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> selectionService.grab(user, 1L, "short"));

        assertEquals(ErrorCode.INVALID_ARGUMENT, exception.errorCode());
        verifyNoInteractions(rateLimitService, stockRedisService, rabbitTemplate);
    }

    @Test
    void soldOutLuaResultReleasesIdempotencyKey() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(orderMapper.findActive(anyLong(), anyLong())).thenReturn(null);
        when(taskMapper.findLatestActive(anyLong(), anyLong())).thenReturn(null);
        when(courseService.requireForSelection(1L)).thenReturn(new com.campus.selection.course.entity.Course());
        when(stockRedisService.tryDeduct(1L, 1L)).thenReturn(StockRedisService.DeductResult.SOLD_OUT);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> selectionService.grab(user, 1L, "idem-key-0001"));

        assertEquals(ErrorCode.SOLD_OUT, exception.errorCode());
        verify(rateLimitService).check("grab", "1");
        verify(redis).delete(anyString());
    }
}
