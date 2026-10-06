package cn.nobeta.bbs.module.user.mapper;

import java.util.Collection;
import java.util.Set;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import cn.nobeta.bbs.module.user.dto.UserProfileDTO;
import cn.nobeta.bbs.module.user.entity.User;
import cn.nobeta.bbs.module.user.entity.UserProfile;
import cn.nobeta.bbs.module.user.vo.UserInfoVO;
import cn.nobeta.bbs.module.user.vo.UserProfileVO;
import cn.nobeta.bbs.module.user.vo.UserBriefVO;

@Mapper
public interface UserMapper {

    /**
     * 根据 id 查询用户
     * @param userId
     * @return
     */
    User selectUserById(@Param("userId") Long userId);

    UserProfileVO selectUserProfileById(@Param("userId") Long userId);

    UserInfoVO selectPublicUserInfoById(@Param("userId") Long userId);

    void insertUserProfile(UserProfile profile);

    /**
     * 根据 id 修改用户个人资料
     * @param userId
     * @param profileDTO
     */
    void updateUserProfileById(@Param("userId") Long userId, @Param("dto") UserProfileDTO dto);

    /**
     * 更新指定用户的头像字段
     * @param userId
     * @param avatarUrl
     */
    void updateUserAvatar(@Param("userId") Long userId, @Param("avatarUrl") String avatarUrl);

    /**
     * 根据 id 批量查询用户简要信息
     * @param userIds
     * @return
     */
    Collection<UserBriefVO> selectAuthorBriefByIds(@Param("userIds") Set<Long> userIds);

    /**
     * 根据 id 查询用户简要信息
     * @param authorId
     * @return
     */
    UserBriefVO selectAuthorBriefById(@Param("authorId") Long authorId);

    

    


}
