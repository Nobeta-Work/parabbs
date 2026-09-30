package cn.nobeta.bbs.module.user.service.impl;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.event.DomainEvent;
import cn.nobeta.bbs.common.event.EventTypes;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.common.util.SnowflakeUtil;
import cn.nobeta.bbs.module.auth.mapper.AuthMapper;
import cn.nobeta.bbs.module.blog.mapper.BlogMapper;
import cn.nobeta.bbs.module.box.OutboxDomainEventPublisher;
import cn.nobeta.bbs.module.file.entity.ImagePurpose;
import cn.nobeta.bbs.module.file.service.FileService;
import cn.nobeta.bbs.module.user.dto.PasswordEditDTO;
import cn.nobeta.bbs.module.user.dto.UserProfileDTO;
import cn.nobeta.bbs.module.user.entity.User;
import cn.nobeta.bbs.module.user.mapper.UserMapper;
import cn.nobeta.bbs.module.user.service.UserService;
import cn.nobeta.bbs.module.user.vo.AvatarVO;
import cn.nobeta.bbs.module.user.vo.UserInfoVO;
import cn.nobeta.bbs.module.user.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final AuthMapper authMapper;
    private final FileService fileService;
    private final PasswordEncoder passwordEncoder;
    private final BlogMapper blogMapper;
    private final OutboxDomainEventPublisher eventPublisher;

    @Override
    public UserProfileVO queryUserProfileById(Long userId) {
        UserProfileVO profile = requireProfile(userId);
        profile.setRoles(authMapper.selectRoleCodesByUserId(userId));
        return profile;
    }

    @Override
    public UserInfoVO queryUserInfoById(Long id) {
        UserInfoVO info = userMapper.selectPublicUserInfoById(id);
        if (info == null) throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "用户不存在");
        return info;
    }

    @Override
    @Transactional
    public void editUserProfile(Long userId, UserProfileDTO dto) {
        UserProfileVO previous = requireProfile(userId);
        fileService.validateManagedImage(userId, ImagePurpose.BACKGROUND, dto.getBackgroundImageUrl());
        try {
            userMapper.updateUserProfileById(userId, dto);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.NICKNAME_DUPLICATE);
        }
        // 作者资料当前直接从 MySQL 查询，没有需要失效的独立资料缓存。
        if (!Objects.equals(previous.getNickname(), dto.getNickname())) {
            for (Long blogId : blogMapper.selectPublishedBlogIdsByAuthor(userId)) {
                eventPublisher.publish(DomainEvent.builder().eventId(SnowflakeUtil.nextId())
                    .eventType(EventTypes.BLOG_UPDATED).aggregateType("blog").aggregateId(blogId)
                    .payload(Map.of("blogId", blogId)).createTime(LocalDateTime.now()).build());
            }
        }
    }

    @Override
    public void editUserPassword(Long userId, PasswordEditDTO dto) {
        User user = userMapper.selectUserById(userId);
        if (user == null) throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "用户不存在");
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_ERROR);
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.NEW_PASSWORD_SAME_AS_OLD);
        }
        userMapper.updateUserPassword(userId, passwordEncoder.encode(dto.getNewPassword()));
    }

    @Override
    @Transactional
    public AvatarVO editUserAvatar(Long userId, MultipartFile file) {
        requireProfile(userId);
        String url = fileService.uploadManagedImage(userId, ImagePurpose.AVATAR, file);
        fileService.validateManagedImage(userId, ImagePurpose.AVATAR, url);
        userMapper.updateUserAvatar(userId, url);
        return AvatarVO.builder().avatarUrl(url).build();
    }

    private UserProfileVO requireProfile(Long id) {
        UserProfileVO profile = userMapper.selectUserProfileById(id);
        if (profile == null) throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "用户不存在");
        return profile;
    }
}
