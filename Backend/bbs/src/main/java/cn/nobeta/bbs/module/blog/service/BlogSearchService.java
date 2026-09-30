package cn.nobeta.bbs.module.blog.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import cn.nobeta.bbs.common.result.PageResult;
import cn.nobeta.bbs.module.blog.dto.BlogPageQuery;
import cn.nobeta.bbs.module.blog.dto.BlogTagBriefRelations;
import cn.nobeta.bbs.module.blog.entity.BlogSearchDocument;
import cn.nobeta.bbs.module.blog.vo.BlogPublicBriefVO;
import cn.nobeta.bbs.module.tag.mapper.TagMapper;
import cn.nobeta.bbs.module.tag.vo.TagBriefVO;
import cn.nobeta.bbs.module.user.mapper.UserMapper;
import cn.nobeta.bbs.module.user.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlogSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final UserMapper userMapper;
    private final TagMapper tagMapper;

    public PageResult<BlogPublicBriefVO> queryPublicBlogPage(BlogPageQuery query) {
        int pageNum = query.getPageNum();
        int pageSize = query.getPageSize();
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (StringUtils.hasText(query.getKeyword())) {
            boolQuery.must(q -> q.multiMatch(m -> m
                .query(query.getKeyword().trim())
                .fields("title", "summary", "content", "authorNickname", "tagNames")
            ));
        }
        if (query.getAuthorId() != null) {
            boolQuery.filter(q -> q.term(t -> t
                .field("authorId")
                .value(v -> v.longValue(query.getAuthorId()))
            ));
        }
        if (query.getTagIds() != null) {
            query.getTagIds().forEach(tagId -> boolQuery.filter(q -> q.term(t -> t
                .field("tagIds")
                .value(v -> v.longValue(tagId))
            )));
        }

        NativeQuery searchQuery = NativeQuery.builder()
            .withQuery(boolQuery.build()._toQuery())
            .withPageable(PageRequest.of(
                pageNum - 1,
                pageSize,
                resolveSort(query)
            ))
            .build();
        SearchHits<BlogSearchDocument> searchHits = elasticsearchOperations.search(
            searchQuery,
            BlogSearchDocument.class
        );
        if (searchHits.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        List<BlogSearchDocument> documents = searchHits.stream()
            .map(SearchHit::getContent)
            .toList();
        Set<Long> authorIds = documents.stream()
            .map(BlogSearchDocument::getAuthorId)
            .collect(Collectors.toSet());
        List<Long> blogIds = documents.stream()
            .map(BlogSearchDocument::getId)
            .toList();
        Map<Long, UserBriefVO> authorMap = userMapper.selectAuthorBriefByIds(authorIds)
            .stream()
            .collect(Collectors.toMap(UserBriefVO::getId, Function.identity()));
        Map<Long, List<TagBriefVO>> tagMap = tagMapper
            .selectBlogTagBriefRelationsByBlogIds(blogIds)
            .stream()
            .collect(Collectors.groupingBy(
                BlogTagBriefRelations::getBlogId,
                Collectors.mapping(
                    relation -> TagBriefVO.builder()
                        .id(relation.getTagId())
                        .name(relation.getTagName())
                        .build(),
                    Collectors.toList()
                )
            ));

        List<BlogPublicBriefVO> records = documents.stream()
            .map(document -> {
                BlogPublicBriefVO blog = BlogPublicBriefVO.builder()
                    .id(document.getId())
                    .title(document.getTitle())
                    .summary(document.getSummary())
                    .coverUrl(document.getCoverUrl())
                    .isPublished(1)
                    .likeCount(document.getLikeCount())
                    .commentsCount(document.getCommentsCount())
                    .createTime(document.getCreateTime())
                    .updateTime(document.getUpdateTime())
                    .author(authorMap.get(document.getAuthorId()))
                    .tags(tagMap.getOrDefault(document.getId(), Collections.emptyList()))
                    .build();
                return blog;
            })
            .toList();
        long total = searchHits.getTotalHits();

        return PageResult.<BlogPublicBriefVO>builder()
            .total(total)
            .pageNum(pageNum)
            .pageSize(pageSize)
            .pages((int) ((total + pageSize - 1) / pageSize))
            .records(records)
            .build();
    }

    private Sort resolveSort(BlogPageQuery query) {
        String field = switch (query.getSortField() == null ? "" : query.getSortField()) {
            case "updateTime" -> "updateTime";
            case "likeCount" -> "likeCount";
            default -> "createTime";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(query.getSortOrder())
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
