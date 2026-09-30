package cn.nobeta.bbs.module.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.nobeta.bbs.common.event.DomainEvent;
import cn.nobeta.bbs.common.event.EventTypes;
import cn.nobeta.bbs.module.auth.mapper.AuthMapper;
import cn.nobeta.bbs.module.blog.mapper.BlogMapper;
import cn.nobeta.bbs.module.box.OutboxDomainEventPublisher;
import cn.nobeta.bbs.module.file.entity.ImagePurpose;
import cn.nobeta.bbs.module.file.service.FileService;
import cn.nobeta.bbs.module.user.dto.UserProfileDTO;
import cn.nobeta.bbs.module.user.mapper.UserMapper;
import cn.nobeta.bbs.module.user.service.impl.UserServiceImpl;
import cn.nobeta.bbs.module.user.vo.UserProfileVO;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserMapper userMapper;
    @Mock AuthMapper authMapper;
    @Mock FileService fileService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock BlogMapper blogMapper;
    @Mock OutboxDomainEventPublisher eventPublisher;
    @InjectMocks UserServiceImpl service;

    @Test
    void editProfile_shouldPublishForPublicBlogs_whenNicknameChanges() {
        when(userMapper.selectUserProfileById(1L)).thenReturn(UserProfileVO.builder().nickname("old").build());
        when(blogMapper.selectPublishedBlogIdsByAuthor(1L)).thenReturn(List.of(11L, 12L));
        UserProfileDTO dto = new UserProfileDTO("new", 2, "", "signature", null);
        service.editUserProfile(1L, dto);
        verify(fileService).validateManagedImage(1L, ImagePurpose.BACKGROUND, null);
        verify(userMapper).updateUserProfileById(1L, dto);
        var events = org.mockito.ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, times(2)).publish(events.capture());
        assertThat(events.getAllValues()).extracting(DomainEvent::getAggregateId).containsExactly(11L, 12L);
        assertThat(events.getAllValues()).allMatch(e -> EventTypes.BLOG_UPDATED.equals(e.getEventType()));
    }

    @Test
    void editProfile_shouldNotPublish_whenOnlySignatureChanges() {
        when(userMapper.selectUserProfileById(1L)).thenReturn(UserProfileVO.builder().nickname("same").build());
        service.editUserProfile(1L, new UserProfileDTO("same", 2, "", "new signature", null));
        verifyNoInteractions(blogMapper, eventPublisher);
    }

    @Test
    void editProfile_shouldNotWrite_whenImageValidationFails() {
        when(userMapper.selectUserProfileById(1L)).thenReturn(UserProfileVO.builder().nickname("same").build());
        doThrow(new IllegalArgumentException()).when(fileService).validateManagedImage(anyLong(), any(), anyString());
        assertThatThrownBy(() -> service.editUserProfile(1L,
            new UserProfileDTO("same", 2, "", "", "https://other.invalid/image.png")))
            .isInstanceOf(IllegalArgumentException.class);
        verify(userMapper, never()).updateUserProfileById(anyLong(), any());
    }
}
