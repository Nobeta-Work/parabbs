package cn.nobeta.bbs.module.user.dto;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import cn.nobeta.bbs.module.blog.dto.BlogEditDTO;
import cn.nobeta.bbs.module.blog.dto.BlogSaveDTO;

class ImageRequestContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void profile_shouldAcceptExplicitNullAndRejectMissingBackground() throws Exception {
        String fields = "\"nickname\":\"Alice\",\"sex\":2,\"race\":\"\",\"signature\":\"\"";
        assertThat(mapper.readValue("{" + fields + ",\"backgroundImageUrl\":null}", UserProfileDTO.class)
            .getBackgroundImageUrl()).isNull();
        assertThatThrownBy(() -> mapper.readValue("{" + fields + "}", UserProfileDTO.class))
            .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void blog_shouldRequireCoverButAllowExplicitNull() throws Exception {
        for (Class<?> dto : new Class<?>[] {BlogSaveDTO.class, BlogEditDTO.class}) {
            assertThat(mapper.readValue("{\"coverUrl\":null}", dto)).isNotNull();
            assertThatThrownBy(() -> mapper.readValue("{}", dto))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        }
    }
}
