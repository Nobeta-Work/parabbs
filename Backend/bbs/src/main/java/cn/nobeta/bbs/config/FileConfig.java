package cn.nobeta.bbs.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Component
@Data
@Validated
@ConfigurationProperties(prefix = "para.file")
public class FileConfig {
    @Valid
    private ManagedConfig managed = new ManagedConfig();
    @Valid
    private ImageConfig image = new ImageConfig();

    /** 本地图片统一使用此目录及对应的静态资源 URL 前缀。 */
    @Data
    public static class ManagedConfig {
        @NotBlank
        private String rootPath;
        @NotBlank
        private String publicBaseUrl;
        @Positive
        private long avatarMaxSize = 10 * 1024 * 1024;
        @Positive
        private long maxSize = 15 * 1024 * 1024;
        @Positive
        private int expire = 2;
    }

    /** 正文图片继续使用已有图床链路。 */
    @Data
    public static class ImageConfig {
        @Positive
        private long maxSize = 15 * 1024 * 1024;
        private List<String> acceptTypes = List.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/jpg");
    }
}
