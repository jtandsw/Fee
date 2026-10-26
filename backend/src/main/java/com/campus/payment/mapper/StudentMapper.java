package com.campus.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.payment.entity.Student;
import com.campus.payment.vo.MajorOptionVO;
import com.campus.payment.vo.StudentOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {

    /** 学籍档案中现存的入学年级（仅在校生），新的在前 */
    List<String> selectScopeGrades();

    /** 学籍档案中现存的学院 */
    List<String> selectScopeColleges();

    /** 学籍档案中现存的「学院-专业」组合 */
    List<MajorOptionVO> selectScopeMajors();

    /**
     * 指定学生范围（CUSTOM）的候选学生：按 ids 精确取（回显已选）或按关键字搜索。
     * 两者都为空时返回前 N 条在校生，作为下拉默认候选。
     */
    List<StudentOptionVO> selectScopeStudents(@Param("keyword") String keyword,
                                              @Param("ids") List<Long> ids,
                                              @Param("limit") int limit);

    /** 按适用范围条件统计覆盖的在校生人数（四个维度取交集，空维度不限制） */
    int countScopeStudents(@Param("grades") List<String> grades,
                           @Param("colleges") List<String> colleges,
                           @Param("majors") List<String> majors,
                           @Param("studentIds") List<String> studentIds);
}
