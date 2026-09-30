package cn.nobeta.bbs.module.blog.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Blog")
public class Blog {
    private Long id;
    private String title;
    private String content;
    private String summary;
    private String coverUrl;
    private Long authorId;
    private Long folderId;
    private Integer isPublished;
    private Integer likeCount;
    private Integer commentsCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
