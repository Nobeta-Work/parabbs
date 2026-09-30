package cn.nobeta.bbs.module.blog.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.module.blog.dto.BlogEditDTO;
import cn.nobeta.bbs.module.blog.dto.BlogSaveDTO;
import cn.nobeta.bbs.module.blog.entity.Blog;
import cn.nobeta.bbs.module.blog.mapper.BlogMapper;
import cn.nobeta.bbs.module.blog.mapper.CommentMapper;
import cn.nobeta.bbs.module.blog.service.impl.BlogServiceImpl;
import cn.nobeta.bbs.module.box.OutboxDomainEventPublisher;
import cn.nobeta.bbs.module.file.entity.ImagePurpose;
import cn.nobeta.bbs.module.file.service.FileService;
import cn.nobeta.bbs.module.folder.mapper.FolderMapper;
import cn.nobeta.bbs.module.like.mapper.LikeMapper;
import cn.nobeta.bbs.module.tag.mapper.TagMapper;
import cn.nobeta.bbs.module.user.mapper.UserMapper;
import cn.nobeta.bbs.support.TestDataFactory;

@ExtendWith(MockitoExtension.class)
class BlogImageServiceTest {
    @Mock BlogMapper blogMapper;
    @Mock CommentMapper commentMapper;
    @Mock UserMapper userMapper;
    @Mock TagMapper tagMapper;
    @Mock FolderMapper folderMapper;
    @Mock LikeMapper likeMapper;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock OutboxDomainEventPublisher eventPublisher;
    @Mock BlogSearchService blogSearchService;
    @Mock FileService fileService;
    @InjectMocks BlogServiceImpl service;

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void create_shouldSaveOwnedCoverOnPrivateBlog() {
        BlogSaveDTO dto = new BlogSaveDTO("https://example.com/cover.png");
        dto.setTitle("title");
        Long id = service.addBlogByUserId(1L, dto);
        var captured = org.mockito.ArgumentCaptor.forClass(Blog.class);
        verify(fileService).validateManagedImage(1L, ImagePurpose.COVER, dto.getCoverUrl());
        verify(blogMapper).insertBlog(captured.capture());
        assertThat(captured.getValue().getId()).isEqualTo(id);
        assertThat(captured.getValue().getCoverUrl()).isEqualTo(dto.getCoverUrl());
        assertThat(captured.getValue().getIsPublished()).isZero();
    }

    @Test
    void edit_shouldClearCoverAndPublishSearchEvent() {
        login();
        when(blogMapper.selectBlogById(10L)).thenReturn(blog(1L));
        BlogEditDTO dto = new BlogEditDTO(null);
        dto.setFolderId(0L);
        dto.setTitle("title");
        dto.setTagIds(List.of());
        service.editBlogById(10L, dto);
        verify(fileService).validateManagedImage(1L, ImagePurpose.COVER, null);
        verify(blogMapper).updateBlogById(10L, dto);
        verify(eventPublisher).publish(argThat(e -> e.getAggregateId().equals(10L)));
    }

    @Test
    void edit_shouldRejectOtherAuthorBeforeChangingImage() {
        login();
        when(blogMapper.selectBlogById(10L)).thenReturn(blog(2L));
        assertThatThrownBy(() -> service.editBlogById(10L, new BlogEditDTO(null)))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(fileService, eventPublisher);
        verify(blogMapper, never()).updateBlogById(anyLong(), any());
    }

    @Test
    void publicDetail_shouldReturnCoverAndCurrentAuthorProfile() {
        when(blogMapper.selectBlogById(10L)).thenReturn(blog(1L));
        var vo = service.getPublicBlogById(10L);
        assertThat(vo.getCoverUrl()).isEqualTo("https://example.com/cover.png");
        verify(userMapper).selectAuthorBriefById(1L);
    }

    private Blog blog(Long authorId) {
        return Blog.builder().id(10L).authorId(authorId).folderId(0L).title("title")
            .coverUrl("https://example.com/cover.png").isPublished(1).likeCount(0).build();
    }
    private void login() {
        var user = TestDataFactory.loginUser();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
