package com.campus.selection.selection.service;

import com.campus.selection.common.config.RabbitConfig;
import com.campus.selection.selection.message.SelectionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SelectionTaskConsumer {

    private final SelectionOrderProcessor processor;
    private final SelectionService selectionService;

    @RabbitListener(queues = RabbitConfig.ORDER_QUEUE)
    public void onMessage(SelectionMessage message) {
        log.info("Consume selection message, requestId={}, studentId={}, courseId={}",
                message.requestId(), message.studentId(), message.courseId());
        try {
            processor.process(message);
        } catch (DuplicateKeyException ex) {
            log.warn("Duplicate order detected, resolve as idempotent success, requestId={}",
                    message.requestId());
            try {
                selectionService.resolveDuplicate(message.requestId());
            } catch (RuntimeException resolveError) {
                throw new AmqpRejectAndDontRequeueException(
                        "Failed to resolve duplicate order for " + message.requestId(), resolveError);
            }
        } catch (Exception ex) {
            log.error("Process selection message failed, requestId={}", message.requestId(), ex);
            try {
                selectionService.failTask(message.requestId(), rootMessage(ex));
            } catch (Exception failError) {
                // 状态落库失败时不要静默 ack，交给容器拒收并进入死信队列，便于人工补偿。
                throw new AmqpRejectAndDontRequeueException(
                        "Failed to mark selection task failed for " + message.requestId(), failError);
            }
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }
}
