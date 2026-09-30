package cn.nobeta.bbs.module.admin.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

import lombok.RequiredArgsConstructor;
import cn.nobeta.bbs.common.enums.RedisKeys;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.common.result.PageResult;
import cn.nobeta.bbs.module.admin.dto.AdminUserEditDTO;
import cn.nobeta.bbs.module.admin.dto.AdminUserPageQuery;
import cn.nobeta.bbs.module.admin.dto.UserRoleBrief;
import cn.nobeta.bbs.module.admin.mapper.AdminMapper;
import cn.nobeta.bbs.module.admin.vo.AdminUserVO;
import cn.nobeta.bbs.module.user.entity.User;

/**
 * 后台用户管理服务
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminMapper adminMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 分页查询用户（携带角色编码）
     * @param query
     * @return
     */
    public PageResult<AdminUserVO> queryUserPage(AdminUserPageQuery query) {

        // 0. 默认分页参数
        if (query.getPageNum() == null) query.setPageNum(1);
        if (query.getPageSize() == null) query.setPageSize(10);

        // 1. 处理 keyword
        String keyword = query.getKeyword();
        query.setKeyword(keyword == null ? null : keyword.trim());

        // 2. 分页查询用户
        Integer pageNum = query.getPageNum();
        Integer pageSize = query.getPageSize();
        PageHelper.startPage(pageNum, pageSize);

        Page<AdminUserVO> users = adminMapper.selectAdminUserPage(query);
        if (users.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        // 3. 批量查询角色
        List<Long> userIds = users.stream().map(AdminUserVO::getId).toList();
        Collection<UserRoleBrief> briefs = adminMapper.selectUserRoleBriefByUserIds(userIds);
        Map<Long, List<String>> roleMap = briefs.stream().collect(Collectors.groupingBy(
                UserRoleBrief::getUserId,
                Collectors.mapping(UserRoleBrief::getRoleCode, Collectors.toList())
        ));

        // 4. 组装返回体
        List<AdminUserVO> records = users.stream().map(user -> {
            user.setRoleCodes(roleMap.getOrDefault(user.getId(), Collections.emptyList()));
            return user;
        }).toList();

        return PageResult.<AdminUserVO>builder()
                .total(users.getTotal())
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pages(users.getPages())
                .records(records)
                .build();
    }

    /**
     * 修改用户状态、角色
     * @param dto
     */
    @Transactional
    public void editUser(AdminUserEditDTO dto) {

        // 1. 存在性检查
        User user = adminMapper.selectUserById(dto.getId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 2. 修改状态
        if (dto.getStatus() != null) {
            adminMapper.updateUserStatus(dto.getId(), dto.getStatus());
        }

        // 3. 覆盖角色
        if (dto.getRoleIds() != null) {
            adminMapper.deleteUserRolesByUserId(dto.getId());
            if (!dto.getRoleIds().isEmpty()) {
                adminMapper.batchInsertUserRoles(dto.getId(), dto.getRoleIds());
            }
        }

        // 4. 状态或角色变更后，清理登录缓存使其立即生效
        clearUserAuthCache(dto.getId());
    }

    /**
     * 封禁用户（软删除：状态置 0）
     * @param userId
     */
    @Transactional
    public void deleteUser(Long userId) {

        // 1. 存在性检查
        User user = adminMapper.selectUserById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 2. 封禁
        adminMapper.updateUserStatus(userId, 0);

        // 3. 清理登录缓存使其立即失效
        clearUserAuthCache(userId);
    }

    /**
     * 清理用户登录缓存（LOGIN_USER / REFRESH_TOKEN）
     * @param userId
     */
    private void clearUserAuthCache(Long userId) {
        redisTemplate.delete(List.of(
            RedisKeys.LOGIN_USER.getFullKey(userId),
            RedisKeys.REFRESH_TOKEN.getFullKey(userId)
        ));
    }

}
