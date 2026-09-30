package cn.nobeta.bbs.module.blog.mapper;


import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.github.pagehelper.Page;

import cn.nobeta.bbs.common.dto.PageQuery;
import cn.nobeta.bbs.module.blog.dto.BlogEditDTO;
import cn.nobeta.bbs.module.blog.dto.BlogPageQuery;
import cn.nobeta.bbs.module.blog.entity.Blog;
import cn.nobeta.bbs.module.blog.entity.BlogSearchDocument;

@Mapper
public interface BlogMapper {
    
    

    /**
     * 插入博客
     * @param blog
     */
    void insertBlog(Blog blog);

    /**
     * 分页查询
     * @param query
     * @return
     */
    Page<Blog> selectBlogPage(BlogPageQuery query);

    /**
     * 根据 id 查找博客
     * @param id
     * @return
     */
    Blog selectBlogById(@Param("id") Long id);

    /**
     * 根据 id 修改博客
     * @param blogId
     * @param blogEditDTO
     */
    void updateBlogById(@Param("blogId") Long blogId, @Param("dto") BlogEditDTO dto);

    /**
     * 根据 id 删除博客
     * @param blogId
     */
    void deleteBlogById(@Param("blogId") Long blogId);

    void incrementCommentsCount(@Param("blogId") Long blogId);

    void decrementCommentsCount(@Param("blogId") Long blogId);

    void reconcileCommentsCount();

    List<Long> selectPublishedBlogIds();

    List<Long> selectPublishedBlogIdsByAuthor(@Param("authorId") Long authorId);

    /**
     * 重置博客目录到根目录
     * @param userId
     * @param ids
     */
    void batchResetBlogFolderByAuthorIdAndFolderIds(
            @Param("userId") Long userId,
            @Param("folderIds") List<Long> folderIds);

    /**
     * 根据目录id查询博客
     * @param authorId 用户隔离
     * @param id
     * @param query
     * @return
     */
    Page<Blog> selectBlogPageByFolderId(
            @Param("authorId") Long authorId,
            @Param("folderId") Long folderId,
            @Param("query") PageQuery query);

    /**
     * 批量移动指定博客到指定目录下
     * @param userId    鉴权，用户隔离
     * @param blogIds
     * @param targetId
     * @return 影响行数
     */
    int updateBlogFolderByAuthorIdAndIds(
            @Param("userId") Long userId,
            @Param("blogIds") List<Long> blogIds,
            @Param("targetId") Long targetId);


    /**
     * 查询博客 Doucment
     * @param blogId
     * @return
     */
    BlogSearchDocument selectSearchDocumentById(@Param("blogId") Long blogId);
}
