package com.campus.selection.selection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.selection.common.api.PageResponse;
import com.campus.selection.common.cache.CacheKeys;
import com.campus.selection.common.config.RabbitConfig;
import com.campus.selection.common.config.SelectionProperties;
import com.campus.selection.common.exception.BusinessException;
import com.campus.selection.common.exception.ErrorCode;
import com.campus.selection.common.security.LoginUser;
import com.campus.selection.course.entity.Course;
import com.campus.selection.course.mapper.CourseMapper;
import com.campus.selection.course.service.CourseService;
import com.campus.selection.domain.CourseStatus;
import com.campus.selection.domain.OrderStatus;
import com.campus.selection.domain.Role;
import com.campus.selection.domain.TaskStatus;
import com.campus.selection.selection.dto.GrabResponse;
import com.campus.selection.selection.dto.OrderResponse;
import com.campus.selection.selection.dto.SelectionStatisticsResponse;
import com.campus.selection.selection.dto.TaskStatusResponse;
import com.campus.selection.selection.entity.SelectionOrder;
import com.campus.selection.selection.entity.SelectionTask;
import com.campus.selection.selection.mapper.SelectionOrderMapper;
import com.campus.selection.selection.mapper.SelectionTaskMapper;
import com.campus.selection.selection.message.SelectionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelectionService {

    private static final int MAX_REASON_LENGTH = 300;

    private final CourseService courseService;
    private final CourseMapper courseMapper;
    private final SelectionTaskMapper taskMapper;
    private final SelectionOrderMapper orderMapper;
    private final StockRedisService stockRedisService;
    private final RateLimitService rateLimitService;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redis;
    private final SelectionProperties properties;

    /**
     * 抢选主流程：幂等键短路 -> 令牌桶 -> 课程校验 -> Lua 原子预扣 -> 异步任务落库 -> MQ 投递。
     */
    public GrabResponse grab(LoginUser user, Long courseId, String idempotencyKey) {
        String key = validateIdempotencyKey(idempotencyKey);
        String idemKey = CacheKeys.idempotency(user.id(), key);

        String existingRequestId = redisGet(idemKey);
        if (existingRequestId != null) {
            return responseForRequest(existingRequestId, user, courseId);
        }

        rateLimitService.check("grab", String.valueOf(user.id()));

        SelectionOrder activeOrder = orderMapper.findActive(user.id(), courseId);
        if (activeOrder != null) {
            throw new BusinessException(ErrorCode.ALREADY_SELECTED,
                    "你已选择该课程，订单号：" + activeOrder.getOrderNo());
        }
        SelectionTask latestTask = taskMapper.findLatestActive(user.id(), courseId);
        if (latestTask != null && latestTask.getStatus() != TaskStatus.SUCCESS) {
            return new GrabResponse(latestTask.getRequestId(), latestTask.getStatus().name(),
                    latestTask.getOrderNo(), "该课程已有处理中的抢选请求");
        }

        Course course = courseService.requireForSelection(courseId);
        String requestId = UUID.randomUUID().toString().replace("-", "");
        boolean acquired = redisSetIfAbsent(idemKey, requestId,
                Duration.ofSeconds(properties.idempotencyTtlSeconds()));
        if (!acquired) {
            String concurrentRequestId = redisGet(idemKey);
            if (concurrentRequestId != null) {
                return responseForRequest(concurrentRequestId, user, courseId);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "请求正在处理中，请稍后重试");
        }

        try {
            StockRedisService.DeductResult deductResult = stockRedisService.tryDeduct(courseId, user.id());
            if (deductResult == StockRedisService.DeductResult.NOT_INITIALIZED) {
                stockRedisService.syncCourseStock(course);
                deductResult = stockRedisService.tryDeduct(courseId, user.id());
            }
            if (deductResult == StockRedisService.DeductResult.SOLD_OUT) {
                redisDelete(idemKey);
                throw new BusinessException(ErrorCode.SOLD_OUT);
            }
            if (deductResult == StockRedisService.DeductResult.DUPLICATE) {
                redisDelete(idemKey);
                throw new BusinessException(ErrorCode.ALREADY_SELECTED, "你已选择该课程");
            }
            if (deductResult != StockRedisService.DeductResult.SUCCESS) {
                redisDelete(idemKey);
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "库存未就绪，请稍后重试");
            }

            SelectionTask task = createTask(requestId, key, user.id(), courseId);
            publish(task);
            return new GrabResponse(requestId, TaskStatus.PENDING.name(), null, "抢选请求已受理，请轮询任务状态");
        } catch (DuplicateKeyException ex) {
            releaseQuietly(courseId, user.id());
            redisDelete(idemKey);
            throw new BusinessException(ErrorCode.CONFLICT, "重复提交，请勿使用相同的幂等键", ex);
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.SERVICE_UNAVAILABLE) {
                releaseQuietly(courseId, user.id());
                redisDelete(idemKey);
            }
            throw ex;
        } catch (RuntimeException ex) {
            releaseQuietly(courseId, user.id());
            redisDelete(idemKey);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public TaskStatusResponse getTask(LoginUser user, String requestId) {
        SelectionTask task = findTask(requestId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "抢选任务不存在");
        }
        if (!task.getStudentId().equals(user.id()) && user.role() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return TaskStatusResponse.from(task);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOrders(LoginUser user, OrderStatus status, int page, int size) {
        Page<SelectionOrder> result = orderMapper.selectPage(new Page<>(page, size),
                Wrappers.<SelectionOrder>lambdaQuery()
                        .eq(SelectionOrder::getStudentId, user.id())
                        .eq(status != null, SelectionOrder::getStatus, status)
                        .orderByDesc(SelectionOrder::getCreatedAt)
                        .orderByDesc(SelectionOrder::getId));
        List<OrderResponse> records = result.getRecords().stream().map(OrderResponse::from).toList();
        return PageResponse.of(records, result.getTotal(), page, size);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(LoginUser user, String orderNo) {
        SelectionOrder order = orderMapper.findOwned(orderNo, user.id());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(LoginUser user, String orderNo) {
        SelectionOrder order = orderMapper.findOwned(orderNo, user.id());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED, "订单已取消");
        }
        Course course = courseMapper.selectById(order.getCourseId());
        if (course == null || LocalDateTime.now().isAfter(course.getSelectEndTime())) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED, "选课已结束，无法取消");
        }

        int updated = orderMapper.cancel(orderNo, user.id());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED, "订单状态已变化，请刷新后重试");
        }
        courseMapper.releaseSeat(order.getCourseId());

        Long courseId = order.getCourseId();
        Long studentId = user.id();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    boolean released = stockRedisService.release(courseId, studentId);
                    if (!released) {
                        log.warn("Cancel order did not release a Redis reservation, orderNo={}", orderNo);
                    }
                    courseService.evictHotCache();
                }
            });
        } else {
            stockRedisService.release(courseId, studentId);
            courseService.evictHotCache();
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public SelectionStatisticsResponse statistics() {
        long publishedCourses = count(courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getStatus, CourseStatus.PUBLISHED)));
        long totalOrders = count(orderMapper.selectCount(null));
        long confirmed = count(orderMapper.selectCount(Wrappers.<SelectionOrder>lambdaQuery()
                .eq(SelectionOrder::getStatus, OrderStatus.CONFIRMED)));
        long cancelled = count(orderMapper.selectCount(Wrappers.<SelectionOrder>lambdaQuery()
                .eq(SelectionOrder::getStatus, OrderStatus.CANCELLED)));
        long todayOrders = orderMapper.countCreatedSince(LocalDate.now().atStartOfDay());

        long totalTasks = count(taskMapper.selectCount(null));
        long pending = count(taskMapper.selectCount(Wrappers.<SelectionTask>lambdaQuery()
                .in(SelectionTask::getStatus, TaskStatus.PENDING, TaskStatus.PROCESSING)));
        long success = count(taskMapper.selectCount(Wrappers.<SelectionTask>lambdaQuery()
                .eq(SelectionTask::getStatus, TaskStatus.SUCCESS)));
        long failed = count(taskMapper.selectCount(Wrappers.<SelectionTask>lambdaQuery()
                .eq(SelectionTask::getStatus, TaskStatus.FAILED)));
        double successRate = totalTasks == 0 ? 0.0
                : BigDecimal.valueOf(success * 100.0 / totalTasks)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new SelectionStatisticsResponse(publishedCourses, totalOrders, confirmed, cancelled,
                todayOrders, totalTasks, pending, success, failed, successRate);
    }

    /**
     * 消费端遇到唯一索引冲突时调用：说明同一学生同一课程已有有效订单，任务应视为成功，不重复补偿库存。
     */
    public void resolveDuplicate(String requestId) {
        SelectionTask task = findTask(requestId);
        if (task == null) {
            return;
        }
        SelectionOrder existing = orderMapper.findActive(task.getStudentId(), task.getCourseId());
        if (existing != null) {
            taskMapper.markSuccess(requestId, existing.getOrderNo());
            return;
        }
        failTask(requestId, "订单唯一索引冲突且未找到已有订单");
    }

    /**
     * 任务失败时先原子更新状态，只有真正从 PENDING/PROCESSING 转为 FAILED 的调用才释放库存，
     * 从而避免消息重投导致重复归还名额。
     */
    public boolean failTask(String requestId, String reason) {
        SelectionTask task = findTask(requestId);
        if (task == null) {
            return false;
        }
        String safeReason = truncate(reason);
        int updated = taskMapper.markFailed(requestId, safeReason);
        if (updated == 0) {
            return false;
        }
        boolean released = stockRedisService.release(task.getCourseId(), task.getStudentId());
        log.warn("Selection task failed, requestId={}, courseId={}, reason={}, stockReleased={}",
                requestId, task.getCourseId(), safeReason, released);
        return true;
    }

    private SelectionTask createTask(String requestId, String idempotencyKey, Long studentId, Long courseId) {
        SelectionTask task = new SelectionTask();
        task.setRequestId(requestId);
        task.setIdempotencyKey(idempotencyKey);
        task.setStudentId(studentId);
        task.setCourseId(courseId);
        task.setStatus(TaskStatus.PENDING);
        taskMapper.insert(task);
        return task;
    }

    private void publish(SelectionTask task) {
        SelectionMessage message = new SelectionMessage(
                task.getRequestId(), task.getStudentId(), task.getCourseId(), System.currentTimeMillis());
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ORDER_ROUTING_KEY, message);
        } catch (AmqpException ex) {
            taskMapper.markFailed(task.getRequestId(), "订单消息投递失败");
            stockRedisService.release(task.getCourseId(), task.getStudentId());
            redisDelete(CacheKeys.idempotency(task.getStudentId(), task.getIdempotencyKey()));
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "订单队列暂时不可用，请稍后重试", ex);
        }
    }

    private GrabResponse responseForRequest(String requestId, LoginUser user, Long courseId) {
        SelectionTask task = findTask(requestId);
        if (task == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "请求正在处理中，请稍后重试");
        }
        if (!task.getStudentId().equals(user.id()) || !task.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        String message = switch (task.getStatus()) {
            case SUCCESS -> "选课成功";
            case FAILED -> "选课失败：" + (task.getFailReason() == null ? "未知原因" : task.getFailReason());
            default -> "抢选请求处理中";
        };
        return new GrabResponse(task.getRequestId(), task.getStatus().name(), task.getOrderNo(), message);
    }

    private SelectionTask findTask(String requestId) {
        return taskMapper.selectOne(Wrappers.<SelectionTask>lambdaQuery()
                .eq(SelectionTask::getRequestId, requestId));
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)
                || !idempotencyKey.trim().matches("[A-Za-z0-9_-]{8,64}")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "Idempotency-Key 必须为 8-64 位字母、数字、下划线或短横线");
        }
        return idempotencyKey.trim();
    }

    private String redisGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "幂等服务暂时不可用，请稍后重试", ex);
        }
    }

    private boolean redisSetIfAbsent(String key, String value, Duration ttl) {
        try {
            Boolean result = redis.opsForValue().setIfAbsent(key, value, ttl);
            return Boolean.TRUE.equals(result);
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "幂等服务暂时不可用，请稍后重试", ex);
        }
    }

    private void redisDelete(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException ex) {
            log.warn("Delete Redis key failed, key={}", key, ex);
        }
    }

    private void releaseQuietly(Long courseId, Long studentId) {
        try {
            stockRedisService.release(courseId, studentId);
        } catch (RuntimeException ex) {
            log.error("Release reservation failed, courseId={}, studentId={}", courseId, studentId, ex);
        }
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    private String truncate(String reason) {
        if (reason == null || reason.isBlank()) {
            return "处理失败";
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
