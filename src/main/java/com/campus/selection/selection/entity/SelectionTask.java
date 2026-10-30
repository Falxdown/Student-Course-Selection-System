package com.campus.selection.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.selection.domain.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("selection_task")
public class SelectionTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private String idempotencyKey;

    private Long studentId;

    private Long courseId;

    private TaskStatus status;

    private String orderNo;

    private String failReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
