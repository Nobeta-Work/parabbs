package cn.nobeta.bbs.module.admin.vo;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBlogVO {

    private Long id;
    private String title;
    private String summary;
    private String coverUrl;
    private Long authorId;
    private String authorName;
    /** 是否公开 0:私有 1:公开 */
    private Integer isPublished;
    private Integer likeCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
