package cn.nobeta.bbs.module.user.service;

import org.springframework.web.multipart.MultipartFile;

import cn.nobeta.bbs.module.user.dto.UserProfileDTO;
import cn.nobeta.bbs.module.user.vo.AvatarVO;
import cn.nobeta.bbs.module.user.vo.UserInfoVO;
import cn.nobeta.bbs.module.user.vo.UserProfileVO;

public interface UserService {



    UserProfileVO queryUserProfileById(Long userId);

    UserInfoVO queryUserInfoById(Long id);

    void editUserProfile(Long userId, UserProfileDTO userProfileDTO);

    AvatarVO editUserAvatar(Long userId, MultipartFile file);

    

}
