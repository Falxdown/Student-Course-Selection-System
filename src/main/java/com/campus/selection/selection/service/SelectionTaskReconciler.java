package com.campus.selection.selection.service;

import com.campus.selection.common.config.RabbitConfig;
import com.campus.selection.common.config.SelectionProperties;
import com.campus.selection.domain.TaskStatus;
import com.campus.selection.selection.entity.SelectionTask;
import com.campus.selection.selection.mapper.SelectionTaskMapper;
import com.campus.selection.selection.message.SelectionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 兜底任务：消息丢失时重投 PENDING 任务；长时间停留在 PROCESSING 的任务标记失败并释放库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelectionTaskReconciler {

    private final SelectionTaskMapper taskMapper;
    private final SelectionService selectionService;
    private final RabbitTemplate rabbitTemplate;
    private final SelectionProperties properties;

    @Scheduled(fixedDelayString = "${app.selection.reconcile-delay-ms:60000}", initialDelay = 60_000)
    public void reconcile() {
        LocalDateTime pendingBefore = LocalDateTime.now().minusSeconds(properties.staleTaskSeconds());
        List<SelectionTask> pendingTasks = taskMapper.findStale(
                TaskStatus.PENDING.name(), pendingBefore, properties.reconcileBatchSize());
        for (SelectionTask task : pendingTasks) {
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ORDER_ROUTING_KEY,
                        new SelectionMessage(task.getRequestId(), task.getStudentId(),
                                task.getCourseId(), System.currentTimeMillis()));
                log.warn("Republished stale selection task, requestId={}", task.getRequestId());
            } catch (AmqpException ex) {
                log.error("Republish stale selection task failed, requestId={}", task.getRequestId(), ex);
            }
        }

        LocalDateTime processingBefore = LocalDateTime.now()
                .minusSeconds(properties.staleTaskSeconds() * 5L);
        List<SelectionTask> processingTasks = taskMapper.findStale(
                TaskStatus.PROCESSING.name(), processingBefore, properties.reconcileBatchSize());
        for (SelectionTask task : processingTasks) {
            selectionService.failTask(task.getRequestId(), "任务处理超时，已自动释放名额");
            log.warn("Failed stale processing task, requestId={}", task.getRequestId());
        }
    }
}
