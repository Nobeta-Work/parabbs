package cn.nobeta.bbs.module.blog.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "parabbs_blog_v1")
public class BlogSearchDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long authorId;

    @Field(
        type = FieldType.Text,
        analyzer = "smartcn",
        searchAnalyzer =  "smartcn"
    )
    private String authorNickname;

    @Field(
        type = FieldType.Text,
        analyzer = "smartcn",
        searchAnalyzer =  "smartcn"
    )
    private String title;

    @Field(
        type = FieldType.Text,
        analyzer = "smartcn",
        searchAnalyzer =  "smartcn"
    )
    private String summary;

    @Field(type = FieldType.Keyword, index = false, docValues = false)
    private String coverUrl;

    @Field(
        type = FieldType.Text,
        analyzer = "smartcn",
        searchAnalyzer =  "smartcn"
    )
    private String content;

    @Field(
        type = FieldType.Long
    )
    private List<Long> tagIds;

    @Field(
        type = FieldType.Text,
        analyzer = "smartcn",
        searchAnalyzer =  "smartcn"
    )
    private List<String> tagNames;

    @Field(
        type = FieldType.Integer
    )
    private Integer likeCount;

    @Field(
        type = FieldType.Integer
    )
    private Integer commentsCount;

    @Field(
        type = FieldType.Date,
        format = DateFormat.date_hour_minute_second
    )
    private LocalDateTime createTime;

    @Field(
        type = FieldType.Date,
        format = DateFormat.date_hour_minute_second
    )
    private LocalDateTime updateTime;
}
