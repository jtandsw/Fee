package com.campus.payment.service;

import com.campus.payment.common.PageResult;
import com.campus.payment.dto.UserQueryDTO;
import com.campus.payment.dto.UserSaveDTO;
import com.campus.payment.vo.ImportResultVO;
import com.campus.payment.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    PageResult<UserVO> page(UserQueryDTO query);

    UserVO create(UserSaveDTO dto);

    UserVO update(Long id, UserSaveDTO dto);

    void updateStatus(Long id, Integer status);

    void resetPassword(Long id, String newPassword);

    ImportResultVO importStudents(MultipartFile file);
}
