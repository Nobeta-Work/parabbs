package cn.nobeta.bbs.module.blog.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class BlogEditDTO {

    @Min(value = 0, message = "目录信息异常")
    @NotNull
    private Long folderId;

    @Min(value = 0, message = "发布状态异常")
    @Max(value = 1, message = "发布状态异常")
    private Integer isPublished;

    @Size(min = 1, max = 20, message = "标题长度介于 1-20 字")
    @NotBlank
    private String title;

    @Size(max = 200, message = "摘要文字不能大于200字")
    private String summary;

    private String content;

    private List<Long> tagIds;

    @Size(max = 2048)
    private String coverUrl;

    @JsonCreator
    public BlogEditDTO(@JsonProperty(value = "coverUrl", required = true) String coverUrl) {
        this.coverUrl = coverUrl;
    }
}
