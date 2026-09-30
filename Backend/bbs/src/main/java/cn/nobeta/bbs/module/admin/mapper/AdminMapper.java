package cn.nobeta.bbs.module.admin.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.github.pagehelper.Page;

import cn.nobeta.bbs.module.admin.dto.AdminBlogPageQuery;
import cn.nobeta.bbs.module.admin.dto.AdminTagPageQuery;
import cn.nobeta.bbs.module.admin.dto.AdminUserPageQuery;
import cn.nobeta.bbs.module.admin.dto.PermPageQuery;
import cn.nobeta.bbs.module.admin.dto.RolePageQuery;
import cn.nobeta.bbs.module.admin.dto.RolePermBrief;
import cn.nobeta.bbs.module.admin.dto.UserRoleBrief;
import cn.nobeta.bbs.module.admin.entity.SysPerm;
import cn.nobeta.bbs.module.admin.entity.SysRole;
import cn.nobeta.bbs.module.admin.vo.AdminBlogVO;
import cn.nobeta.bbs.module.admin.vo.AdminUserVO;
import cn.nobeta.bbs.module.admin.vo.AdminTagVO;
import cn.nobeta.bbs.module.blog.entity.Blog;
import cn.nobeta.bbs.module.tag.entity.Tag;
import cn.nobeta.bbs.module.user.entity.User;

@Mapper
public interface AdminMapper {

    /* ==================== 角色 ==================== */

    /**
     * 分页查询角色
     */
    Page<SysRole> selectRolePage(RolePageQuery query);

    /**
     * 根据 id 查询角色
     */
    SysRole selectRoleById(@Param("roleId") Long roleId);

    /**
     * 根据角色编码查询角色
     */
    SysRole selectRoleByCode(@Param("roleCode") String roleCode);

    /**
     * 根据角色名查询角色
     */
    SysRole selectRoleByName(@Param("roleName") String roleName);

    /**
     * 新建角色
     */
    void insertRole(SysRole role);

    /**
     * 修改角色信息
     */
    void updateRole(SysRole role);

    /**
     * 批量查询角色持有的权限简要信息
     */
    Collection<RolePermBrief> selectRolePermBriefByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    /**
     * 删除角色全部权限关系
     */
    void deleteRolePermsByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入角色-权限关系
     */
    void batchInsertRolePerms(@Param("roleId") Long roleId, @Param("permIds") List<Long> permIds);

    /* ==================== 权限 ==================== */

    /**
     * 分页查询权限
     */
    Page<SysPerm> selectPermPage(PermPageQuery query);

    /**
     * 根据 id 查询权限
     */
    SysPerm selectPermById(@Param("permId") Long permId);

    /**
     * 新建权限
     */
    void insertPerm(SysPerm perm);

    /**
     * 修改权限
     */
    void updatePerm(SysPerm perm);

    /* ==================== 用户 ==================== */

    /**
     * 分页查询用户
     */
    Page<AdminUserVO> selectAdminUserPage(AdminUserPageQuery query);

    /**
     * 根据 id 查询用户
     */
    User selectUserById(@Param("userId") Long userId);

    /**
     * 批量查询用户持有的角色编码
     */
    Collection<UserRoleBrief> selectUserRoleBriefByUserIds(@Param("userIds") Collection<Long> userIds);

    /**
     * 修改用户状态
     */
    void updateUserStatus(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 删除用户全部角色关系
     */
    void deleteUserRolesByUserId(@Param("userId") Long userId);

    /**
     * 批量插入用户-角色关系
     */
    void batchInsertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    /* ==================== 博客 ==================== */

    /**
     * 分页查询博客（带作者名）
     */
    Page<AdminBlogVO> selectAdminBlogPage(AdminBlogPageQuery query);

    /**
     * 根据 id 查询博客
     */
    Blog selectBlogById(@Param("blogId") Long blogId);

    /**
     * 修改博客公开状态
     */
    void updateBlogStatus(@Param("blogId") Long blogId, @Param("isPublished") Integer isPublished);

    /**
     * 删除博客
     */
    void deleteBlogById(@Param("blogId") Long blogId);

    /**
     * 删除博客标签关系
     */
    void deleteBlogTagsByBlogId(@Param("blogId") Long blogId);

    /* ==================== 标签 ==================== */

    /**
     * 分页查询标签
     */
    Page<AdminTagVO> selectAdminTagPage(AdminTagPageQuery query);

    /**
     * 根据 id 查询标签
     */
    Tag selectTagById(@Param("tagId") Long tagId);

    /**
     * 新建标签
     */
    void insertAdminTag(Tag tag);

    /**
     * 修改标签
     */
    void updateAdminTag(Tag tag);

}
