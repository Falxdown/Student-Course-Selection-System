package com.campus.selection.selection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.selection.selection.entity.SelectionOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface SelectionOrderMapper extends BaseMapper<SelectionOrder> {

    @Select("""
            SELECT * FROM selection_order
             WHERE student_id = #{studentId} AND course_id = #{courseId} AND active = 1
             LIMIT 1
            """)
    SelectionOrder findActive(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    @Select("""
            SELECT * FROM selection_order
             WHERE order_no = #{orderNo} AND student_id = #{studentId}
             LIMIT 1
            """)
    SelectionOrder findOwned(@Param("orderNo") String orderNo, @Param("studentId") Long studentId);

    @Update("""
            UPDATE selection_order
               SET status = 'CANCELLED',
                   active = NULL,
                   cancelled_at = NOW(),
                   updated_at = NOW()
             WHERE order_no = #{orderNo}
               AND student_id = #{studentId}
               AND status = 'CONFIRMED'
               AND active = 1
            """)
    int cancel(@Param("orderNo") String orderNo, @Param("studentId") Long studentId);

    @Select("SELECT COUNT(*) FROM selection_order WHERE created_at >= #{start}")
    long countCreatedSince(@Param("start") LocalDateTime start);

    @Select("SELECT student_id FROM selection_order WHERE course_id = #{courseId} AND active = 1")
    List<Long> findActiveStudentIds(@Param("courseId") Long courseId);
}
