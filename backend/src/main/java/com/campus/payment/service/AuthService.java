package com.campus.payment.service;

import com.campus.payment.dto.ChangePasswordDTO;
import com.campus.payment.dto.LoginDTO;
import com.campus.payment.vo.LoginVO;
import com.campus.payment.vo.UserVO;

public interface AuthService {

    LoginVO login(LoginDTO dto);

    LoginVO refresh(String refreshToken);

    void logout(String accessToken, String refreshToken);

    UserVO currentUser();

    void changePassword(ChangePasswordDTO dto);
}
