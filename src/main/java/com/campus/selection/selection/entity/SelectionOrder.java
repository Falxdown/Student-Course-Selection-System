package com.campus.selection.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.selection.domain.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("selection_order")
public class SelectionOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String requestId;

    private Long studentId;

    private Long courseId;

    private String courseCode;

    private String courseName;

    private OrderStatus status;

    /**
     * 1 = 有效订单，NULL = 已取消。MySQL 唯一索引允许多个 NULL，
     * 因此同一课程可以取消多次后重新选择，同时保证最多一个有效订单。
     */
    private Integer active;

    private LocalDateTime cancelledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
