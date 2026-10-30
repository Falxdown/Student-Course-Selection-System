package com.campus.selection.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.selection.common.api.PageResponse;
import com.campus.selection.common.cache.CacheKeys;
import com.campus.selection.common.config.SelectionProperties;
import com.campus.selection.common.exception.BusinessException;
import com.campus.selection.common.exception.ErrorCode;
import com.campus.selection.course.dto.CourseRequest;
import com.campus.selection.course.dto.CourseResponse;
import com.campus.selection.course.dto.CourseStockResponse;
import com.campus.selection.course.entity.Course;
import com.campus.selection.course.mapper.CourseMapper;
import com.campus.selection.domain.CourseStatus;
import com.campus.selection.selection.service.StockRedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final StockRedisService stockRedisService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SelectionProperties selectionProperties;

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> listPublished(int page, int size, String keyword, String category, String sort) {
        LambdaQueryWrapper<Course> wrapper = Wrappers.<Course>lambdaQuery()
                .eq(Course::getStatus, CourseStatus.PUBLISHED)
                .eq(StringUtils.hasText(category), Course::getCategory, category)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(Course::getName, keyword)
                        .or()
                        .like(Course::getTeacher, keyword)
                        .or()
                        .like(Course::getCourseCode, keyword));

        if ("hot".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(Course::getSelectedCount);
        } else {
            wrapper.orderByAsc(Course::getSelectStartTime);
        }
        wrapper.orderByAsc(Course::getId);

        Page<Course> result = courseMapper.selectPage(new Page<>(page, size), wrapper);
        List<Course> records = result.getRecords();
        List<Integer> stocks = stockRedisService.remainingStocks(records);
        List<CourseResponse> responses = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            responses.add(CourseResponse.from(records.get(i), stocks.get(i)));
        }
        return PageResponse.of(responses, result.getTotal(), page, size);
    }

    @Transactional(readOnly = true)
    public CourseResponse getPublished(Long courseId) {
        Course course = requirePublished(courseId);
        return CourseResponse.from(course, stockRedisService.remainingStock(course));
    }

    @Transactional(readOnly = true)
    public CourseStockResponse stock(Long courseId) {
        Course course = requirePublished(courseId);
        return CourseStockResponse.from(course, stockRedisService.remainingStock(course));
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> hot(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        String cacheKey = CacheKeys.hotCourses(safeLimit);
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<CourseResponse>>() {
                });
            }
        } catch (DataAccessException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Read hot course cache failed, reason={}", ex.getMessage());
        }

        Page<Course> page = courseMapper.selectPage(new Page<>(1, safeLimit),
                Wrappers.<Course>lambdaQuery()
                        .eq(Course::getStatus, CourseStatus.PUBLISHED)
                        .orderByDesc(Course::getSelectedCount)
                        .orderByAsc(Course::getId));
        List<Course> records = page.getRecords();
        List<Integer> stocks = stockRedisService.remainingStocks(records);
        List<CourseResponse> responses = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            responses.add(CourseResponse.from(records.get(i), stocks.get(i)));
        }
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(responses),
                    java.time.Duration.ofSeconds(selectionProperties.hotCacheSeconds()));
        } catch (DataAccessException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Write hot course cache failed, reason={}", ex.getMessage());
        }
        return responses;
    }

    @Transactional
    public CourseResponse create(CourseRequest request) {
        validateWindow(request);
        String courseCode = normalizeCode(request.courseCode());
        Long duplicate = courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getCourseCode, courseCode));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "课程编号已存在");
        }

        Course course = new Course();
        apply(course, request, courseCode);
        course.setSelectedCount(0);
        courseMapper.insert(course);
        evictHotCache();
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            stockRedisService.syncCourseStock(course);
        }
        return CourseResponse.from(course, stockRedisService.remainingStock(course));
    }

    @Transactional
    public CourseResponse update(Long courseId, CourseRequest request) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        validateWindow(request);
        if (request.capacity() < course.getSelectedCount()) {
            throw new BusinessException(ErrorCode.CONFLICT, "容量不能小于当前已选人数");
        }
        String courseCode = normalizeCode(request.courseCode());
        Long duplicate = courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getCourseCode, courseCode)
                .ne(Course::getId, courseId));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "课程编号已存在");
        }

        apply(course, request, courseCode);
        int updated = courseMapper.updateById(course);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "课程已被其他管理员修改，请刷新后重试");
        }
        evictHotCache();
        if (course.getStatus() == CourseStatus.PUBLISHED || course.getStatus() == CourseStatus.CLOSED) {
            stockRedisService.syncCourseStock(course);
        }
        return CourseResponse.from(course, stockRedisService.remainingStock(course));
    }

    public Course requirePublished(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在或未发布");
        }
        return course;
    }

    public Course requireForSelection(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.COURSE_NOT_AVAILABLE);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(course.getSelectStartTime()) || now.isAfter(course.getSelectEndTime())) {
            throw new BusinessException(ErrorCode.SELECTION_NOT_OPEN);
        }
        return course;
    }

    private void apply(Course course, CourseRequest request, String courseCode) {
        course.setCourseCode(courseCode);
        course.setName(request.name().trim());
        course.setTeacher(request.teacher().trim());
        course.setCategory(request.category().trim());
        course.setCredits(request.credits());
        course.setCapacity(request.capacity());
        course.setSelectStartTime(request.selectStartTime());
        course.setSelectEndTime(request.selectEndTime());
        course.setStatus(request.status());
        course.setDescription(request.description());
    }

    private void validateWindow(CourseRequest request) {
        if (!request.selectEndTime().isAfter(request.selectStartTime())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "选课结束时间必须晚于开始时间");
        }
    }

    private String normalizeCode(String courseCode) {
        return courseCode.trim().toUpperCase(Locale.ROOT);
    }

    public void evictHotCache() {
        try {
            List<String> keys = new ArrayList<>();
            for (int i = 1; i <= 20; i++) {
                keys.add(CacheKeys.hotCourses(i));
            }
            redis.delete(keys);
        } catch (DataAccessException ex) {
            log.warn("Evict hot course cache failed, reason={}", ex.getMessage());
        }
    }
}
