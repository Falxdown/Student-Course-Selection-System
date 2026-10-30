package com.campus.selection.selection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.selection.common.cache.CacheKeys;
import com.campus.selection.common.exception.BusinessException;
import com.campus.selection.common.exception.ErrorCode;
import com.campus.selection.course.entity.Course;
import com.campus.selection.course.mapper.CourseMapper;
import com.campus.selection.domain.CourseStatus;
import com.campus.selection.selection.mapper.SelectionOrderMapper;
import com.campus.selection.selection.mapper.SelectionTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class StockRedisService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> stockDeductScript;
    private final DefaultRedisScript<Long> stockReleaseScript;
    private final CourseMapper courseMapper;
    private final SelectionOrderMapper orderMapper;
    private final SelectionTaskMapper taskMapper;

    public StockRedisService(StringRedisTemplate redis,
                             @Qualifier("stockDeductScript") DefaultRedisScript<Long> stockDeductScript,
                             @Qualifier("stockReleaseScript") DefaultRedisScript<Long> stockReleaseScript,
                             CourseMapper courseMapper,
                             SelectionOrderMapper orderMapper,
                             SelectionTaskMapper taskMapper) {
        this.redis = redis;
        this.stockDeductScript = stockDeductScript;
        this.stockReleaseScript = stockReleaseScript;
        this.courseMapper = courseMapper;
        this.orderMapper = orderMapper;
        this.taskMapper = taskMapper;
    }

    public enum DeductResult {
        SUCCESS,
        SOLD_OUT,
        DUPLICATE,
        NOT_INITIALIZED
    }

    /**
     * 库存以 Redis 为准做原子预扣减；Lua 中的 set 同时承担同课程重复请求的快速拦截。
     */
    public DeductResult tryDeduct(Long courseId, Long studentId) {
        try {
            Long result = redis.execute(
                    stockDeductScript,
                    List.of(CacheKeys.stock(courseId), CacheKeys.selectedStudents(courseId)),
                    String.valueOf(studentId)
            );
            if (result == null) {
                return DeductResult.NOT_INITIALIZED;
            }
            return switch (result.intValue()) {
                case 1 -> DeductResult.SUCCESS;
                case 2 -> DeductResult.DUPLICATE;
                case -1 -> DeductResult.NOT_INITIALIZED;
                default -> DeductResult.SOLD_OUT;
            };
        } catch (DataAccessException ex) {
            log.error("Redis stock deduction failed, courseId={}", courseId, ex);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "库存服务暂时不可用，请稍后重试", ex);
        }
    }

    /**
     * 幂等释放：只有确实在 Redis 中持有占位的学生才会归还库存。
     */
    public boolean release(Long courseId, Long studentId) {
        try {
            Long released = redis.execute(
                    stockReleaseScript,
                    List.of(CacheKeys.stock(courseId), CacheKeys.selectedStudents(courseId)),
                    String.valueOf(studentId)
            );
            return released != null && released == 1L;
        } catch (DataAccessException ex) {
            log.error("Redis stock release failed, courseId={}, studentId={}", courseId, studentId, ex);
            return false;
        }
    }

    public int remainingStock(Course course) {
        try {
            String value = redis.opsForValue().get(CacheKeys.stock(course.getId()));
            if (value == null) {
                syncCourseStock(course);
                value = redis.opsForValue().get(CacheKeys.stock(course.getId()));
            }
            if (value == null) {
                return fallbackStock(course);
            }
            return Math.max(0, Integer.parseInt(value));
        } catch (DataAccessException | NumberFormatException ex) {
            log.warn("Read stock from Redis failed, fallback to database, courseId={}, reason={}",
                    course.getId(), ex.getMessage());
            return fallbackStock(course);
        }
    }

    /**
     * 从数据库重建某门课的库存和已占位学生集合。
     * 已确认订单 + 处理中任务共同占用容量，避免重启时把在途请求算成可售名额。
     */
    public void syncCourseStock(Course course) {
        try {
            Set<Long> occupied = new LinkedHashSet<>(orderMapper.findActiveStudentIds(course.getId()));
            occupied.addAll(taskMapper.findReservedStudentIds(course.getId()));
            int remaining = Math.max(0, course.getCapacity() - occupied.size());

            redis.opsForValue().set(CacheKeys.stock(course.getId()), String.valueOf(remaining));
            String selectedKey = CacheKeys.selectedStudents(course.getId());
            redis.delete(selectedKey);
            if (!occupied.isEmpty()) {
                String[] members = occupied.stream().map(String::valueOf).toArray(String[]::new);
                redis.opsForSet().add(selectedKey, members);
            }
            log.info("Synced stock courseId={} capacity={} occupied={} remaining={}",
                    course.getId(), course.getCapacity(), occupied.size(), remaining);
        } catch (DataAccessException ex) {
            log.error("Sync stock to Redis failed, courseId={}, reason={}", course.getId(), ex.getMessage());
        }
    }

    public void initializeAll() {
        if (!isRedisAvailable()) {
            log.warn("Redis is unavailable at startup; skip stock initialization and retry on demand");
            return;
        }
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .in(Course::getStatus, CourseStatus.PUBLISHED, CourseStatus.CLOSED));
        int initialized = 0;
        for (Course course : courses) {
            try {
                syncCourseStock(course);
                initialized++;
            } catch (RuntimeException ex) {
                log.error("Initialize stock failed, courseId={}", course.getId(), ex);
            }
        }
        log.info("Stock initialization finished, courses={}", initialized);
    }

    private boolean isRedisAvailable() {
        try {
            redis.hasKey(CacheKeys.stock(-1L));
            return true;
        } catch (DataAccessException ex) {
            log.warn("Redis ping failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 批量读取库存；缓存缺失时逐门回源，课程列表接口不会因为个别 key 缺失而报错。
     */
    public List<Integer> remainingStocks(List<Course> courses) {
        List<Integer> result = new ArrayList<>(courses.size());
        for (Course course : courses) {
            result.add(remainingStock(course));
        }
        return result;
    }

    private int fallbackStock(Course course) {
        return Math.max(0, course.getCapacity() - course.getSelectedCount());
    }
}
