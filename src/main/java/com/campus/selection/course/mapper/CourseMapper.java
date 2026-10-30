package com.campus.selection.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.selection.course.entity.Course;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface CourseMapper extends BaseMapper<Course> {

    /**
     * 数据库侧的最终兜底：即使 Redis 扣减成功，也绝不允许已选人数超过容量。
     */
    @Update("""
            UPDATE course
               SET selected_count = selected_count + 1,
                   version = version + 1,
                   updated_at = NOW()
             WHERE id = #{courseId}
               AND status = 'PUBLISHED'
               AND selected_count < capacity
            """)
    int reserveSeat(@Param("courseId") Long courseId);

    @Update("""
            UPDATE course
               SET selected_count = GREATEST(selected_count - 1, 0),
                   version = version + 1,
                   updated_at = NOW()
             WHERE id = #{courseId}
            """)
    int releaseSeat(@Param("courseId") Long courseId);

    @Select("SELECT * FROM course WHERE id = #{courseId} FOR UPDATE")
    Course selectForUpdate(@Param("courseId") Long courseId);
}
