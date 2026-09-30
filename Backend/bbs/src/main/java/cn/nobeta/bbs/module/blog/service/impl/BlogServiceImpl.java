package cn.nobeta.bbs.module.blog.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.nobeta.bbs.common.enums.RedisKeys;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.event.DomainEvent;
import cn.nobeta.bbs.common.event.EventTypes;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.common.result.PageResult;
import cn.nobeta.bbs.common.util.SnowflakeUtil;
import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;
import cn.nobeta.bbs.module.blog.dto.BlogEditDTO;
import cn.nobeta.bbs.module.blog.dto.BlogPageQuery;
import cn.nobeta.bbs.module.blog.dto.BlogSaveDTO;
import cn.nobeta.bbs.module.blog.dto.BlogTagBriefRelations;
import cn.nobeta.bbs.module.blog.entity.Blog;
import cn.nobeta.bbs.module.blog.mapper.BlogMapper;
import cn.nobeta.bbs.module.blog.mapper.CommentMapper;
import cn.nobeta.bbs.module.blog.service.BlogSearchService;
import cn.nobeta.bbs.module.blog.service.BlogService;
import cn.nobeta.bbs.module.blog.vo.BlogPrivateDetailVO;
import cn.nobeta.bbs.module.blog.vo.BlogPublicBriefVO;
import cn.nobeta.bbs.module.blog.vo.BlogPublicDetailVO;
import cn.nobeta.bbs.module.box.OutboxDomainEventPublisher;
import cn.nobeta.bbs.module.folder.entity.Folder;
import cn.nobeta.bbs.module.file.entity.ImagePurpose;
import cn.nobeta.bbs.module.file.service.FileService;
import cn.nobeta.bbs.module.folder.mapper.FolderMapper;
import cn.nobeta.bbs.module.like.mapper.LikeMapper;
import cn.nobeta.bbs.module.tag.mapper.TagMapper;
import cn.nobeta.bbs.module.tag.vo.TagBriefVO;
import cn.nobeta.bbs.module.user.mapper.UserMapper;
import cn.nobeta.bbs.module.user.vo.UserBriefVO;
import cn.nobeta.bbs.security.util.SecurityUtil;

@Service
@Slf4j
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService{
    
    private final BlogMapper blogMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final TagMapper tagMapper;
    private final FolderMapper folderMapper;
    private final LikeMapper likeMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final OutboxDomainEventPublisher eventPublisher;
    private final BlogSearchService blogSearchService;
    private final FileService fileService;

    /**
     * 分页查询
     * @param pageQueryDTO
     * @return
     */
    @Override
    public PageResult<BlogPublicBriefVO> queryPublicBlogPage(BlogPageQuery query) {
        try {
            return blogSearchService.queryPublicBlogPage(query);
        } catch (RuntimeException e) {
            log.warn("Elasticsearch 查询博客失败，降级到 MySQL", e);
            return queryPublicBlogPageFromDatabase(query);
        }
    }

    private PageResult<BlogPublicBriefVO> queryPublicBlogPageFromDatabase(BlogPageQuery query) {
        // 1.提取分页参数
        Integer pageNum = query.getPageNum();
        Integer pageSize = query.getPageSize();
        
        
        // 2. 处理 keyword
        if (query.getKeyword() != null) {
            // 去除前后空格
            query.setKeyword(query.getKeyword().trim());
        }

        
        // 2.开启PageHelper分页查询
        PageHelper.startPage(pageNum, pageSize);
        
        // 2.1 分页查询 blog
        Page<Blog> blogPage = blogMapper.selectBlogPage(query);
        if (blogPage.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        // 2.2 收集关联 id: blogIds:blog_tag,authorIds:sys_user
        List<Long> blogIds = blogPage.stream().map(Blog::getId).toList();
        Set<Long> authorIds = blogPage.stream().map(Blog::getAuthorId).collect(Collectors.toSet());

        // 2.3 批量查询
        Map<Long, UserBriefVO> authorMap = userMapper.selectAuthorBriefByIds(authorIds)
                .stream().collect(Collectors.toMap(UserBriefVO::getId, Function.identity()));
        Map<Long, List<TagBriefVO>> tagMap = tagMapper.selectBlogTagBriefRelationsByBlogIds(blogIds)
                .stream().collect(Collectors.groupingBy(
                    BlogTagBriefRelations::getBlogId,
                    Collectors.mapping(
                        r -> TagBriefVO.builder()
                                .id(r.getTagId())
                                .name(r.getTagName())
                                .build(),
                        Collectors.toList()
                    )
                ));

        // 2.4 组装 records
        List<BlogPublicBriefVO> records = (List<BlogPublicBriefVO>) blogPage.stream().map(
            blog -> BlogPublicBriefVO.builder()
                    .id(blog.getId())
                    .title(blog.getTitle())
                    .summary(blog.getSummary())
                .coverUrl(blog.getCoverUrl())
                    .isPublished(blog.getIsPublished())
                    .likeCount(blog.getLikeCount())
                    .commentsCount(blog.getCommentsCount())
                    .createTime(blog.getCreateTime())
                    .updateTime(blog.getUpdateTime())
                    .author(authorMap.get(blog.getAuthorId()))
                    .tags(tagMap.getOrDefault(blog.getId(), Collections.emptyList()))
                    .build()
        ).toList();

        // 3. 返回
        return PageResult.<BlogPublicBriefVO>builder()
                .total(blogPage.getTotal())
                .pageNum(blogPage.getPageNum())
                .pageSize(blogPage.getPageSize())
                .pages(blogPage.getPages())
                .records(records)
                .build();
    }

    /**
     * 创建博客
     * @param userId
     * @param blogSaveDTO
     * @return
     */
    @Override
    @Transactional
    public Long addBlogByUserId(Long userId, BlogSaveDTO blogSaveDTO) {

        // 1. 生成雪花 ID
        Long blogId = SnowflakeUtil.nextId();

        // 2. 校验目录
        Long folderId = blogSaveDTO.getFolderId() == null ? 0L : blogSaveDTO.getFolderId();
        if (folderId > 0) {
            Folder folder = folderMapper.selectFolderById(folderId);
            if (folder == null || !folder.getAuthorId().equals(userId)) {
                throw new BusinessException(ResultCode.FOLDER_NOT_FOUND);
            }
        }
        
        fileService.validateManagedImage(userId, ImagePurpose.COVER, blogSaveDTO.getCoverUrl());

        // 3. 封装 blog
        Blog blog = Blog.builder()
                .id(blogId)
                .authorId(userId)
                .folderId(folderId)
                .isPublished(0)
                .coverUrl(blogSaveDTO.getCoverUrl())
                .title(blogSaveDTO.getTitle())
                .build();

        // 4. 插入数据库

        try {
            blogMapper.insertBlog(blog);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.BLOG_TITLE_DUPLICATE);
        }

        return blogId;
    }

    /**
     * 根据 id 查询公开博客
     * @param id
     * @return
     */
    @Override
    public BlogPublicDetailVO getPublicBlogById(Long id) {

        // 1. 获得博客
        Blog blog = blogMapper.selectBlogById(id);
        if (blog == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "博客不存在");
        }

        // 2. 校验博客发布状态
        if (blog.getIsPublished() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 3. 查找作者信息
        UserBriefVO author = userMapper.selectAuthorBriefById(blog.getAuthorId());

        // 4. 查找标签信息
        List<TagBriefVO> tags = tagMapper.selectTagBriefByBlogId(id);

        // 5. 查找 isLiked
        boolean isLiked = false;
        Integer likeCount = blog.getLikeCount();
        UserAuthInfo loginUser = SecurityUtil.getLoginUser();
        if (loginUser != null) {
            Long userId = loginUser.getUser().getId();
            String key = RedisKeys.LIKE_BLOG.getFullKey(id);
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                // TODO: 未锁机制
                // 5.1 缓存未命中，DB 回源
                List<Long> likerIds = likeMapper.selectLikerIdsByBlogId(id);
                
                // TODO: 缓存穿透

                String[] members = likerIds.stream().map(Object::toString).toArray(String[]::new);
                if (members.length > 0) {
                    stringRedisTemplate.opsForSet().add(key, members);
                    stringRedisTemplate.expire(key, Duration.ofSeconds(RedisKeys.LIKE_BLOG.getDefaultTtl()));
                }
                
            }
            // 5.2 缓存查找
            isLiked = Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(
                key,
                userId.toString()
            ));
            Long cachedLikeCount = stringRedisTemplate.opsForSet().size(key);
            if (cachedLikeCount != null) {
                likeCount = cachedLikeCount.intValue();
            }
        }

        return BlogPublicDetailVO.builder()
                .id(blog.getId())
                .title(blog.getTitle())
                .summary(blog.getSummary())
                .coverUrl(blog.getCoverUrl())
                .isPublished(blog.getIsPublished())
                .author(author)
                .tags(tags)
                .likeCount(likeCount)
                .commentsCount(blog.getCommentsCount())
                .createTime(blog.getCreateTime())
                .updateTime(blog.getUpdateTime())
                .content(blog.getContent())
                .isLiked(isLiked)
                .build();
                
    }

    /**
     * 查询指定私人博客详情
     * @param id 博客id
     */
    @Override
    public BlogPrivateDetailVO getPrivateBlogById(Long id) {
        // 1. 获得博客
        Blog blog = blogMapper.selectBlogById(id);
        if (blog == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "博客不存在");
        }

        // 2. 校验作者信息
        UserAuthInfo loginUser = SecurityUtil.getLoginUser();
        if (loginUser == null || !loginUser.getUser().getId().equals(blog.getAuthorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 3. 查找作者信息
        UserBriefVO author = userMapper.selectAuthorBriefById(blog.getAuthorId());

        // 4. 查找标签信息
        List<TagBriefVO> tags = tagMapper.selectTagBriefByBlogId(id);

        

        return BlogPrivateDetailVO.builder()
                .id(blog.getId())
                .title(blog.getTitle())
                .summary(blog.getSummary())
                .coverUrl(blog.getCoverUrl())
                .isPublished(blog.getIsPublished())
                .author(author)
                .tags(tags)
                .likeCount(blog.getLikeCount())
                .commentsCount(blog.getCommentsCount())
                .createTime(blog.getCreateTime())
                .updateTime(blog.getUpdateTime())
                .folderId(blog.getFolderId())
                .content(blog.getContent())
                .build();
    }

    /**
     * 根据blogid删除博客
     * @param blogId
     */
    @Override
    @Transactional
    public void deleteBlogById(Long blogId) {
        // 1. 查询 blog
        Blog blog = blogMapper.selectBlogById(blogId);
        if (blog == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        
        // 2. 校验权限
        UserAuthInfo loginUser = SecurityUtil.getLoginUser();
        if (loginUser == null || !loginUser.getUser().getId().equals(blog.getAuthorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 3. 删除数据
        commentMapper.deleteCommentsByBlogId(blogId);
        blogMapper.deleteBlogById(blogId);

        // 4. 删除 blog_tag 关系
        tagMapper.deleteBlogTagRelationsByBlogId(blogId);

        // 5. 变更消息记录
        DomainEvent event = DomainEvent.builder()
                .eventId(SnowflakeUtil.nextId())
                .eventType(EventTypes.BLOG_DELETED)
                .aggregateType("blog")
                .aggregateId(blogId)
                .payload(Map.of(
                    "blogId", blogId
                ))
                .createTime(LocalDateTime.now())
                .build();
        eventPublisher.publish(event);
    }

    /**
     * 更新博客
     * @param blogId
     * @param blogEditDTO
     */
    @Transactional
    @Override
    public void editBlogById(Long blogId, BlogEditDTO blogEditDTO) {
        // 1.根据blogId查询原始博客信息
        Blog blog = blogMapper.selectBlogById(blogId);
        if (blog == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "博客不存在");
        }

        // 2.权限校验
        // TODO: 仅作者校验，无管理员权限
        UserAuthInfo loginUser = (UserAuthInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (loginUser == null || !loginUser.getUser().getId().equals(blog.getAuthorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 3.目录校验
        if (blogEditDTO.getFolderId() > 0) {
            Folder folder = folderMapper.selectFolderById(blogEditDTO.getFolderId());
            if (folder == null || !folder.getAuthorId().equals(loginUser.getUser().getId())) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
        }

        fileService.validateManagedImage(blog.getAuthorId(), ImagePurpose.COVER, blogEditDTO.getCoverUrl());

        // 4. blog 更新
        blogMapper.updateBlogById(blogId, blogEditDTO);

        // 5. blog_tag 双步更新
        List<Long> tagIds = blogEditDTO.getTagIds();

        tagMapper.deleteBlogTagRelationsByBlogId(blogId);

        if (tagIds != null && !tagIds.isEmpty()) {
            tagMapper.batchInsertBlogTagReliations(blogId, tagIds);
        }

        // 6. 变更消息记录
        DomainEvent event = DomainEvent.builder()
                .eventId(SnowflakeUtil.nextId())
                .eventType(EventTypes.BLOG_UPDATED)
                .aggregateType("blog")
                .aggregateId(blogId)
                .payload(Map.of(
                    "blogId", blogId
                ))
                .createTime(LocalDateTime.now())
                .build();
        eventPublisher.publish(event);
    }



}
