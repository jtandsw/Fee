package com.campus.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.payment.entity.Notice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {

    /** 当前角色可见的已发布通知（按角色过滤） */
    IPage<Notice> selectVisiblePage(IPage<Notice> page,
                                    @Param("role") String role,
                                    @Param("userId") Long userId);

    /** 未读数：可见且未读过 */
    long countUnread(@Param("role") String role, @Param("userId") Long userId);
}
