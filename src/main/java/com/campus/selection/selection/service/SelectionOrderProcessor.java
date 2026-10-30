package com.campus.selection.selection.service;

import com.campus.selection.common.exception.BusinessException;
import com.campus.selection.common.exception.ErrorCode;
import com.campus.selection.common.util.OrderNoGenerator;
import com.campus.selection.course.entity.Course;
import com.campus.selection.course.mapper.CourseMapper;
import com.campus.selection.course.service.CourseService;
import com.campus.selection.domain.OrderStatus;
import com.campus.selection.domain.TaskStatus;
import com.campus.selection.selection.entity.SelectionOrder;
import com.campus.selection.selection.entity.SelectionTask;
import com.campus.selection.selection.mapper.SelectionOrderMapper;
import com.campus.selection.selection.mapper.SelectionTaskMapper;
import com.campus.selection.selection.message.SelectionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelectionOrderProcessor {

    private final SelectionTaskMapper taskMapper;
    private final SelectionOrderMapper orderMapper;
    private final CourseMapper courseMapper;
    private final CourseService courseService;

    /**
     * 消费端事务：订单落库 + 数据库余量兜底 + 任务状态更新，三者在同一事务内完成。
     * 消息重投时先看任务终态与唯一索引，重复消息只会命中已有订单，不会重复扣减。
     */
    @Transactional
    public void process(SelectionMessage message) {
        SelectionTask task = taskMapper.selectForUpdate(message.requestId());
        if (task == null) {
            log.warn("Selection task not found, ignore message, requestId={}", message.requestId());
            return;
        }
        if (task.getStatus() == TaskStatus.SUCCESS || task.getStatus() == TaskStatus.FAILED) {
            log.info("Selection task already finished, requestId={}, status={}",
                    message.requestId(), task.getStatus());
            return;
        }
        if (task.getStatus() == TaskStatus.PENDING && taskMapper.claim(message.requestId()) == 0) {
            return;
        }

        SelectionOrder existing = orderMapper.findActive(task.getStudentId(), task.getCourseId());
        if (existing != null) {
            taskMapper.markSuccess(message.requestId(), existing.getOrderNo());
            return;
        }

        Course course = courseMapper.selectById(task.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }

        SelectionOrder order = new SelectionOrder();
        order.setOrderNo(OrderNoGenerator.next());
        order.setRequestId(message.requestId());
        order.setStudentId(task.getStudentId());
        order.setCourseId(course.getId());
        order.setCourseCode(course.getCourseCode());
        order.setCourseName(course.getName());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setActive(1);
        orderMapper.insert(order);

        // Redis 已经预扣过库存，这里再做一次数据库条件更新，形成第二道防超卖闸门。
        int reserved = courseMapper.reserveSeat(course.getId());
        if (reserved == 0) {
            throw new BusinessException(ErrorCode.SOLD_OUT, "课程名额已抢完");
        }
        taskMapper.markSuccess(message.requestId(), order.getOrderNo());
        courseService.evictHotCache();
        log.info("Selection order created, requestId={}, orderNo={}, studentId={}, courseId={}",
                message.requestId(), order.getOrderNo(), task.getStudentId(), task.getCourseId());
    }
}
