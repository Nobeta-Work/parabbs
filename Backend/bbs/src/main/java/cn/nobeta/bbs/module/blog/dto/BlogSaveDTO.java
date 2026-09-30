package cn.nobeta.bbs.module.blog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class BlogSaveDTO {

    @Size(min = 1, max = 50, message = "标题字数限制 1-50 字")
    @NotBlank
    private String title;   // 标题

    @Min(value = 0, message = "目录异常")
    private Long folderId = 0L;  // 目录 ID，默认指向根目录

    @Size(max = 2048)
    private String coverUrl;

    @JsonCreator
    public BlogSaveDTO(@JsonProperty(value = "coverUrl", required = true) String coverUrl) {
        this.coverUrl = coverUrl;
    }
}
