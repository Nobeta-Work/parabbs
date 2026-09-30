package cn.nobeta.bbs.module.folder.service.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

import lombok.RequiredArgsConstructor;
import cn.nobeta.bbs.common.dto.PageQuery;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.common.result.PageResult;
import cn.nobeta.bbs.module.blog.dto.BlogTagBriefRelations;
import cn.nobeta.bbs.module.blog.entity.Blog;
import cn.nobeta.bbs.module.blog.mapper.BlogMapper;
import cn.nobeta.bbs.module.blog.vo.BlogPrivateBriefVO;
import cn.nobeta.bbs.module.folder.dto.FolderBlogsMoveDTO;
import cn.nobeta.bbs.module.folder.dto.FolderSaveDTO;
import cn.nobeta.bbs.module.folder.entity.Folder;
import cn.nobeta.bbs.module.folder.mapper.FolderMapper;
import cn.nobeta.bbs.module.folder.service.FolderService;
import cn.nobeta.bbs.module.folder.util.FolderUtil;
import cn.nobeta.bbs.module.folder.vo.FolderTree;
import cn.nobeta.bbs.module.tag.mapper.TagMapper;
import cn.nobeta.bbs.module.tag.vo.TagBriefVO;
import cn.nobeta.bbs.module.user.mapper.UserMapper;
import cn.nobeta.bbs.module.user.vo.UserBriefVO;
import cn.nobeta.bbs.security.util.SecurityUtil;

@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderMapper folderMapper;
    private final BlogMapper blogMapper;
    private final UserMapper userMapper;
    private final TagMapper tagMapper;


    /**
     * 创建目录
     * @param userId 所属用户 id
     * @param folderSaveDTO
     */
    @Override
    @Transactional
    public void createFolder(Long userId, FolderSaveDTO folderSaveDTO) {
        
        // 1. 校验 parentFolder
        Long parentId = folderSaveDTO.getParentId();
        Integer parentLevel = 0;            // 最低允许一级目录
        String parentPath = "";      // 根目录 

        if (parentId > 0) {
            Folder parentFolder = folderMapper.selectFolderById(parentId);
            // 1.1 校验所属用户一致性
            if (parentFolder == null || !parentFolder.getAuthorId().equals(userId)) {
                throw new BusinessException(ResultCode.FOLDER_OPERAION_NOT_ALLOWED);
            }
            // 1.2 校验深度
            parentLevel = parentFolder.getLevel();
            if (parentLevel > 3) {
                throw new BusinessException(ResultCode.FOLDER_TOO_DEEP);
            }
            // 1.3 更新父目录等级和路径
            parentPath = parentFolder.getPath();
        }

        // 3. 生成目录实体
        // Transaction
        Folder folder = Folder.builder()
                .parentId(parentId)
                .name(folderSaveDTO.getName())
                .level(parentLevel + 1)
                .authorId(userId)
                .path("0")  // 0，完全非法 (path NOT NULL)
                .build();

        // 4. 插入数据库，捕获重复异常
        
        try {
            folderMapper.insertFolder(folder);
            Long id = folder.getId();
            String path = parentPath + "/" + id;
            folderMapper.updateFolderPathById(id, path);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.FOLDER_NAME_DUPLICATE);
        }

    }

    /**
     * 重命名目录
     * @param id
     * @param name
     * @return
     */
    @Override
    public void renameFolder(Long id, String name) {
        // 1. 查询 folder
        Folder folder = folderMapper.selectFolderById(id);
        if (folder == null) {
            throw new BusinessException(ResultCode.FOLDER_NOT_FOUND);
        }

        // 2. 判断 authorId 与重名
        Long userId = SecurityUtil.getLoginUserId();
        if (!userId.equals(folder.getAuthorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        if (name.equals(folder.getName())) {
            throw new BusinessException(ResultCode.FOLDER_NAME_NOT_CHANGED);
        }

        // 3. 重命名
        folderMapper.updateFolderName(id, name);
    }

    /**
     * 移动目录
     * @param id
     * @param targetId
     * @return
     */
    @Override
    @Transactional
    public void moveFolderById(Long id, Long targetParentId) {
        // 1. 校验 folder 存在, authorId 匹配
        Folder folder = folderMapper.selectFolderById(id);
        Long userId = SecurityUtil.getLoginUserId();

        if (folder == null) {
            throw new BusinessException(ResultCode.FOLDER_NOT_FOUND);
        } else if (!userId.equals(folder.getAuthorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 2. 校验 parentFoler

        Integer parentLevel = 0;
        String parentPath = "";
        
        // 2.1 校验 targetId 不是当前 folder.parentId
        if (targetParentId.equals(id) || targetParentId.equals(folder.getParentId())) {
            throw new BusinessException(ResultCode.FOLDER_NOT_CHANGED);
        }

        // 2.2 校验 parentFolder 存在
        if (targetParentId > 0) {

            Folder parentFolder = folderMapper.selectFolderById(targetParentId);
            if (parentFolder == null) {
                throw new BusinessException(ResultCode.FOLDER_NOT_FOUND);
            } else if (!userId.equals(parentFolder.getAuthorId())) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }

            // 2.3 校验 parentFolder 不是 folder 子孙
            parentPath = parentFolder.getPath();

            if (parentPath.equals(folder.getPath()) || parentPath.startsWith(folder.getPath() + "/")) {
                throw new BusinessException(ResultCode.PATH_NOT_AVALIABLE);
            }

            // 2.4 查询子目录最大深度
            Integer maxLevel = folderMapper.selectMaxLevelInSubtree(id);
            Integer newMaxLevel = parentFolder.getLevel() + maxLevel - folder.getLevel();
            if (newMaxLevel > 4) {
                throw new BusinessException(ResultCode.FOLDER_TOO_DEEP);
            }

            // 2.5 更新父目录参数
            parentLevel = parentFolder.getLevel();
            parentPath = parentFolder.getPath();
        }

        // 3.1 重新计算 folder.level
        Integer level = parentLevel + 1;

        // 3.2 重建 folder.path
        String path = parentPath + "/" + folder.getId();

        // 3.3 递归更新所有子孙的 level 和 path

        folderMapper.updateFolderParentIdWithoutChildren(id, targetParentId);
        folderMapper.updateSubtreeFolderLevelAndPath(userId, id, level, path);
    }

    /**
     * 删除目录
     * @param id
     * @return
     */
    @Override
    @Transactional
    public void deleteFolder(Long id) {
        Long userId = SecurityUtil.getLoginUserId();
        // 1. 校验 folder 存在，authorId 匹配
        Folder folder = folderMapper.selectFolderById(id);
        if (folder == null || !folder.getAuthorId().equals(userId)) {
            throw new BusinessException(ResultCode.FOLDER_OPERAION_NOT_ALLOWED);
        }

        // 2. Transaction
        // 2.1 收集所有要删除的子树目录 ids
        List<Long> ids = folderMapper.selectSubtreeFolderIdsByAuthorIdAndPath(userId, folder.getPath());

        // 2.2 将相关目录下所有博客 folder_id 置为 0 (移动至根目录)
        blogMapper.batchResetBlogFolderByAuthorIdAndFolderIds(userId, ids);

        // 2.3 DELETE
        folderMapper.batchDeleteFolderByAuthorIdAndIds(userId, ids);
    }

    /**
     * 获得当前用户的 FolderTree
     * @return
     */
    @Override
    public FolderTree queryFolderTreeByUserId(Long userId) {
        // 方案：查询用户所有目录，在 Service 层组装为树
        FolderTree root = FolderUtil.buildRootByUserId(userId);

        // 1. 查询所有所属目录
        List<Folder> folders = folderMapper.selectSubtreeFoldersByAuthorIdAndParentId(userId, 0L);

        // 2. 组装为树
        // 2.1 构建节点 map
        Map<Long, FolderTree> nodeMap = new LinkedHashMap<>();

        for (Folder folder : folders) nodeMap.put(folder.getId(), FolderUtil.folderToFolderTree(folder));
        nodeMap.put(0L, root);

        // 2.2 遍历目录，添加到父目录的 children 中
        for (Folder folder : folders) {

            FolderTree node = nodeMap.get(folder.getId());
            FolderTree parent = nodeMap.get(folder.getParentId());

            if (parent == null) {
                throw new BusinessException(ResultCode.FAIL);
            }

            parent.getChildren().add(node);
        }
        

        // 3. 返回目录树
        return root;
    }

    /**
     * 查询指定 id 目录下的所有博客
     * @param id
     * @return
     */
    @Override
    public PageResult<BlogPrivateBriefVO> queryBlogPageByFolderId(Long id, PageQuery query) {
        // 1. 校验 folder 和 authorId
        Long userId = SecurityUtil.getLoginUserId();
        if (id > 0) {
            Folder folder = folderMapper.selectFolderById(id);
            if (folder == null || !folder.getAuthorId().equals(userId)) {
                throw new BusinessException(ResultCode.FOLDER_OPERAION_NOT_ALLOWED);
            }
        }

        // 2. 分页查询 blog (folderId)
        // 2.1 提取参数
        Integer pageNum = query.getPageNum();
        Integer pageSize = query.getPageSize();

        // 2.2 开启 PageHelper 分页查询
        PageHelper.startPage(pageNum, pageSize);

        // 2.3 分页查询 Blog
        Page<Blog> blogPage = blogMapper.selectBlogPageByFolderId(userId, id, query);
        if (blogPage.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        List<Long> blogIds = blogPage.stream().map(Blog::getId).toList();
        Set<Long> authorIds = blogPage.stream().map(Blog::getAuthorId).collect(Collectors.toSet());
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

        // 2.4 组装
        List<BlogPrivateBriefVO> records = (List<BlogPrivateBriefVO>) blogPage.stream().map(
            blog -> BlogPrivateBriefVO.builder()
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
                    .folderId(blog.getFolderId())
                    .build()
        ).toList();

        // 3. 返回
        return PageResult.<BlogPrivateBriefVO>builder()
                .total(blogPage.getTotal())
                .pageNum(blogPage.getPageNum())
                .pageSize(blogPage.getPageSize())
                .pages(blogPage.getPages())
                .records(records)
                .build();
    }

    /**
     * 批量移动博客切换目录
     * @param folderBlogsMoveDTO
     * @return
     */
    @Override
    @Transactional
    public void moveBlogsToFolder(FolderBlogsMoveDTO folderBlogsMoveDTO) {
        
        // 1. 提取信息
        Long userId = SecurityUtil.getLoginUserId();
        List<Long> blogIds = folderBlogsMoveDTO.getBlogIds();
        Long targetId = folderBlogsMoveDTO.getTargetId();

        if (targetId > 0) {
            Folder folder = folderMapper.selectFolderById(targetId);
            if (folder == null || !folder.getAuthorId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
        }

        // 2. Transaction
        // 2.1 改库，权限校验下沉 sql
        int cnt = blogMapper.updateBlogFolderByAuthorIdAndIds(userId, blogIds, targetId);
        // 2.2 乐观锁思想，修改数量不匹配，回滚
        if (cnt != blogIds.size()) {
            throw new BusinessException(ResultCode.FOLDER_OPERAION_NOT_ALLOWED);
        }
    }

}
