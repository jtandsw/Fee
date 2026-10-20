package com.campus.payment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.payment.common.BusinessException;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.ResultCode;
import com.campus.payment.common.constant.Role;
import com.campus.payment.dto.UserQueryDTO;
import com.campus.payment.dto.UserSaveDTO;
import com.campus.payment.entity.Student;
import com.campus.payment.entity.SysUser;
import com.campus.payment.mapper.StudentMapper;
import com.campus.payment.mapper.SysUserMapper;
import com.campus.payment.security.SecurityUtils;
import com.campus.payment.service.UserService;
import com.campus.payment.vo.ImportResultVO;
import com.campus.payment.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserMapper userMapper;
    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> page(UserQueryDTO query) {
        IPage<SysUser> page = userMapper.selectPage(query.toPage(),
                Wrappers.<SysUser>lambdaQuery()
                        .eq(StringUtils.hasText(query.getRole()), SysUser::getRole, query.getRole())
                        .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(SysUser::getUsername, query.getKeyword())
                                .or()
                                .like(SysUser::getRealName, query.getKeyword()))
                        .orderByAsc(SysUser::getId));
        List<Long> userIds = page.getRecords().stream().map(SysUser::getId).toList();
        Map<Long, Student> students = new HashMap<>();
        if (!userIds.isEmpty()) {
            students = studentMapper.selectList(Wrappers.<Student>lambdaQuery()
                            .in(Student::getUserId, userIds))
                    .stream().collect(Collectors.toMap(Student::getUserId, s -> s, (a, b) -> a));
        }
        Map<Long, Student> finalStudents = students;
        return PageResult.of(page.convert(user -> toVO(user, finalStudents.get(user.getId()))));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(UserSaveDTO dto) {
        Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        boolean isStudent = Role.STUDENT.name().equals(dto.getRole());
        if (isStudent) {
            validateStudentInfo(dto);
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(
                StringUtils.hasText(dto.getPassword()) ? dto.getPassword() : DEFAULT_PASSWORD));
        user.setRealName(dto.getRealName());
        user.setRole(dto.getRole());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        if (isStudent) {
            Student student = new Student();
            student.setUserId(user.getId());
            student.setStudentNo(dto.getStudentNo());
            student.setCollege(dto.getCollege());
            student.setMajor(dto.getMajor());
            student.setClassName(dto.getClassName());
            student.setGrade(dto.getGrade());
            student.setStatus(1);
            try {
                studentMapper.insert(student);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ResultCode.DATA_CONFLICT, "学号已存在");
            }
            return toVO(user, student);
        }
        return toVO(user, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO update(Long id, UserSaveDTO dto) {
        SysUser user = requireUser(id);
        if (!user.getRole().equals(dto.getRole())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不支持修改用户角色，请新建账号");
        }
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        userMapper.updateById(user);

        Student student = null;
        if (Role.STUDENT.name().equals(user.getRole())) {
            student = studentMapper.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId, id));
            if (student == null) {
                throw new BusinessException(ResultCode.DATA_CONFLICT, "该学生账号缺少学籍信息");
            }
            if (StringUtils.hasText(dto.getStudentNo())) {
                student.setStudentNo(dto.getStudentNo());
            }
            if (StringUtils.hasText(dto.getCollege())) {
                student.setCollege(dto.getCollege());
            }
            if (StringUtils.hasText(dto.getMajor())) {
                student.setMajor(dto.getMajor());
            }
            if (StringUtils.hasText(dto.getClassName())) {
                student.setClassName(dto.getClassName());
            }
            if (dto.getGrade() != null) {
                student.setGrade(dto.getGrade());
            }
            studentMapper.updateById(student);
        }
        return toVO(user, student);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (id.equals(SecurityUtils.getUserId()) && status != null && status == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不能禁用当前登录账号");
        }
        SysUser user = requireUser(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        SysUser user = requireUser(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importStudents(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请上传学生名单 CSV");
        }
        List<String> lines;
        try {
            lines = new String(file.getBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件读取失败");
        }
        ImportResultVO result = new ImportResultVO();
        String encodedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);
        boolean first = true;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replace("\uFEFF", "").trim();
            if (line.isEmpty()) {
                continue;
            }
            if (first) {
                first = false;
                if (line.contains("学号") || line.toLowerCase().contains("student")) {
                    continue;
                }
            }
            result.setTotal(result.getTotal() + 1);
            String[] cols = line.split(",", -1);
            if (cols.length < 6) {
                result.setFailed(result.getFailed() + 1);
                result.addMessage("第 " + (i + 1) + " 行：列数不足，应为 学号,姓名,学院,专业,班级,年级");
                continue;
            }
            String studentNo = cols[0].trim();
            String realName = cols[1].trim();
            String college = cols[2].trim();
            String major = cols[3].trim();
            String className = cols[4].trim();
            int grade;
            try {
                grade = new BigDecimal(cols[5].trim()).intValue();
            } catch (NumberFormatException e) {
                result.setFailed(result.getFailed() + 1);
                result.addMessage("第 " + (i + 1) + " 行：年级格式错误");
                continue;
            }
            if (studentNo.isEmpty() || realName.isEmpty() || college.isEmpty()
                    || major.isEmpty() || className.isEmpty()) {
                result.setFailed(result.getFailed() + 1);
                result.addMessage("第 " + (i + 1) + " 行：存在必填字段为空");
                continue;
            }
            Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, studentNo));
            if (exists != null && exists > 0) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            try {
                SysUser user = new SysUser();
                user.setUsername(studentNo);
                user.setPassword(encodedPassword);
                user.setRealName(realName);
                user.setRole(Role.STUDENT.name());
                user.setStatus(1);
                userMapper.insert(user);

                Student student = new Student();
                student.setUserId(user.getId());
                student.setStudentNo(studentNo);
                student.setCollege(college);
                student.setMajor(major);
                student.setClassName(className);
                student.setGrade(grade);
                student.setStatus(1);
                studentMapper.insert(student);
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                log.warn("import student failed at line {}: {}", i + 1, e.getMessage());
                result.setFailed(result.getFailed() + 1);
                result.addMessage("第 " + (i + 1) + " 行：写入失败（学号可能重复）");
            }
        }
        return result;
    }

    private void validateStudentInfo(UserSaveDTO dto) {
        if (!StringUtils.hasText(dto.getStudentNo()) || !StringUtils.hasText(dto.getCollege())
                || !StringUtils.hasText(dto.getMajor()) || !StringUtils.hasText(dto.getClassName())
                || dto.getGrade() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "学生账号需填写学号、学院、专业、班级、年级");
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
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
}
