package cn.nobeta.bbs.module.user.mapper;

import static org.assertj.core.api.Assertions.*;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import cn.nobeta.bbs.module.blog.dto.BlogEditDTO;
import cn.nobeta.bbs.module.user.dto.UserProfileDTO;

class ImageMapperContractTest {
    @Test
    void mappers_shouldParseAndWriteNullImagesWithoutReadingLegacyProfileColumns() throws Exception {
        Configuration configuration = new Configuration();
        for (String name : new String[] {"Auth", "User", "Admin", "Blog", "File"}) {
            String resource = "mapper/" + name + "Mapper.xml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        String profileSql = configuration.getMappedStatement(UserMapper.class.getName() + ".updateUserProfileById")
            .getBoundSql(Map.of("userId", 1L, "dto", new UserProfileDTO("Alice", 2, "", "", null))).getSql();
        assertThat(profileSql).contains("UPDATE user_profile", "background_image_url = ?", "signature = ?");
        BlogEditDTO edit = new BlogEditDTO(null);
        edit.setTitle("title");
        String blogSql = configuration.getMappedStatement("cn.nobeta.bbs.module.blog.mapper.BlogMapper.updateBlogById")
            .getBoundSql(Map.of("blogId", 1L, "dto", edit)).getSql();
        assertThat(blogSql).contains("cover_url = ?");
        String publicSql = configuration.getMappedStatement(UserMapper.class.getName() + ".selectPublicUserInfoById")
            .getBoundSql(Map.of("userId", 1L)).getSql();
        assertThat(publicSql).doesNotContain("password", "status", "username").contains("user_profile");
    }
}
