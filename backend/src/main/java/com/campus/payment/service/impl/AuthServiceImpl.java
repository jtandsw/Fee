package com.campus.payment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.payment.common.BusinessException;
import com.campus.payment.common.ResultCode;
import com.campus.payment.common.constant.Role;
import com.campus.payment.dto.ChangePasswordDTO;
import com.campus.payment.dto.LoginDTO;
import com.campus.payment.entity.Student;
import com.campus.payment.entity.SysUser;
import com.campus.payment.mapper.StudentMapper;
import com.campus.payment.mapper.SysUserMapper;
import com.campus.payment.security.JwtUtil;
import com.campus.payment.security.LoginUser;
import com.campus.payment.security.SecurityUtils;
import com.campus.payment.security.TokenStore;
import com.campus.payment.service.AuthService;
import com.campus.payment.vo.LoginVO;
import com.campus.payment.vo.UserVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);
        return issueTokens(user, findStudent(user));
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtUtil.parse(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!JwtUtil.TYPE_REFRESH.equals(claims.get("typ", String.class))
                || !tokenStore.isRefreshActive(claims.getId())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = userMapper.selectById(((Number) claims.get("uid")).longValue());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 刷新令牌一次性使用：旧的作废，签发新的一对，降低泄露风险
        tokenStore.revokeRefresh(claims.getId());
        return issueTokens(user, findStudent(user));
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (StringUtils.hasText(accessToken)) {
            try {
                Claims claims = jwtUtil.parse(stripBearer(accessToken));
                long remainSeconds = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
                tokenStore.blacklist(claims.getId(), Math.max(remainSeconds, 1));
            } catch (Exception e) {
                log.debug("logout with invalid access token: {}", e.getMessage());
            }
        }
        if (StringUtils.hasText(refreshToken)) {
            try {
                tokenStore.revokeRefresh(jwtUtil.parse(refreshToken).getId());
            } catch (Exception e) {
                log.debug("logout with invalid refresh token: {}", e.getMessage());
            }
        }
    }

    @Override
    public UserVO currentUser() {
        LoginUser loginUser = SecurityUtils.getCurrentUser();
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto) {
        SysUser user = userMapper.selectById(SecurityUtils.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "原密码不正确");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "新密码不能与原密码相同");
        }
        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(patch);
    }

    private LoginVO issueTokens(SysUser user, Student student) {
        LoginUser loginUser = new LoginUser(user.getId(),
                student == null ? null : student.getId(),
                user.getUsername(), user.getRealName(), user.getRole());
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();
        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtUtil.generateAccessToken(loginUser, accessJti));
        vo.setRefreshToken(jwtUtil.generateRefreshToken(loginUser, refreshJti));
        vo.setExpiresIn(jwtUtil.getAccessExpireSeconds());
        vo.setUser(toVO(user, student));
        tokenStore.saveRefresh(refreshJti, jwtUtil.getRefreshExpireSeconds());
        return vo;
    }

    private Student findStudent(SysUser user) {
        if (!Role.STUDENT.name().equals(user.getRole())) {
            return null;
        }
        return studentMapper.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId, user.getId()));
    }

    private UserVO toVO(SysUser user) {
        return toVO(user, findStudent(user));
    }

    private UserVO toVO(SysUser user, Student student) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        if (student != null) {
            vo.setStudentNo(student.getStudentNo());
            vo.setCollege(student.getCollege());
            vo.setMajor(student.getMajor());
            vo.setClassName(student.getClassName());
            vo.setGrade(student.getGrade());
        }
        return vo;
    }

    private String stripBearer(String token) {
        return token.startsWith("Bearer ") ? token.substring(7).trim() : token;
    }
}
