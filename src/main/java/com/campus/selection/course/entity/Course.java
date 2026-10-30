package com.campus.selection.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.campus.selection.domain.CourseStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String courseCode;

    private String name;

    private String teacher;

    private String category;

    private BigDecimal credits;

    private Integer capacity;

    private Integer selectedCount;

    private LocalDateTime selectStartTime;

    private LocalDateTime selectEndTime;

    private CourseStatus status;

    private String description;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
