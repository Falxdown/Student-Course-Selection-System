package com.campus.selection.selection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.selection.selection.entity.SelectionTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface SelectionTaskMapper extends BaseMapper<SelectionTask> {

    @Select("SELECT * FROM selection_task WHERE request_id = #{requestId} FOR UPDATE")
    SelectionTask selectForUpdate(@Param("requestId") String requestId);

    @Update("""
            UPDATE selection_task
               SET status = 'PROCESSING', updated_at = NOW()
             WHERE request_id = #{requestId} AND status = 'PENDING'
            """)
    int claim(@Param("requestId") String requestId);

    @Update("""
            UPDATE selection_task
               SET status = 'SUCCESS',
                   order_no = #{orderNo},
                   fail_reason = NULL,
                   updated_at = NOW()
             WHERE request_id = #{requestId}
               AND status IN ('PENDING', 'PROCESSING')
            """)
    int markSuccess(@Param("requestId") String requestId, @Param("orderNo") String orderNo);

    @Update("""
            UPDATE selection_task
               SET status = 'FAILED',
                   fail_reason = #{reason},
                   updated_at = NOW()
             WHERE request_id = #{requestId}
               AND status IN ('PENDING', 'PROCESSING')
            """)
    int markFailed(@Param("requestId") String requestId, @Param("reason") String reason);

    @Select("""
            SELECT * FROM selection_task
             WHERE student_id = #{studentId}
               AND course_id = #{courseId}
               AND status IN ('PENDING', 'PROCESSING', 'SUCCESS')
             ORDER BY id DESC
             LIMIT 1
            """)
    SelectionTask findLatestActive(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    @Select("""
            SELECT * FROM selection_task
             WHERE status = #{status} AND updated_at < #{before}
             ORDER BY updated_at ASC
             LIMIT #{limit}
            """)
    List<SelectionTask> findStale(@Param("status") String status,
                                  @Param("before") LocalDateTime before,
                                  @Param("limit") int limit);

    @Select("""
            SELECT student_id FROM selection_task
             WHERE course_id = #{courseId}
               AND status IN ('PENDING', 'PROCESSING')
            """)
    List<Long> findReservedStudentIds(@Param("courseId") Long courseId);
}
