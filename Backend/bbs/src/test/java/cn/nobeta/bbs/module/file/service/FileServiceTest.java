package cn.nobeta.bbs.module.file.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.common.util.GithubFileUtil;
import cn.nobeta.bbs.config.FileConfig;
import cn.nobeta.bbs.module.file.entity.ImageFile;
import cn.nobeta.bbs.module.file.entity.ImagePurpose;
import cn.nobeta.bbs.module.file.mapper.FileMapper;

class FileServiceTest {
    @TempDir Path root;
    FileMapper mapper;
    FileServiceImpl service;
    @BeforeEach
    void setUp() {
        mapper = mock(FileMapper.class);
        FileConfig config = new FileConfig();
        config.getManaged().setRootPath(root.toString());
        config.getManaged().setPublicBaseUrl("https://example.com/images/");
        service = new FileServiceImpl(config, mapper, mock(GithubFileUtil.class),
            mock(ImageCleanupService.class), new MockEnvironment());
    }

    @Test
    void validateImage_shouldRejectOtherOwnerOrPurpose() throws Exception {
        Files.write(root.resolve("image.png"), new byte[] {1});
        String url = "https://example.com/image.png";
        when(mapper.selectByUrlForUpdate(url)).thenReturn(ImageFile.builder().userId(2L)
            .purpose(ImagePurpose.COVER).storageKey("image.png").build());
        assertThatThrownBy(() -> service.validateManagedImage(1L, ImagePurpose.COVER, url))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.validateManagedImage(2L, ImagePurpose.BACKGROUND, url))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateImage_shouldAllowNullAndRejectTemporaryOrSignedUrls() {
        service.validateManagedImage(1L, ImagePurpose.COVER, null);
        for (String url : new String[] {"blob:temp", "data:image/png;base64,a", "relative.png",
            "https://example.com/image.png?signature=secret", "http://example.com/image.png"}) {
            assertThatThrownBy(() -> service.validateManagedImage(1L, ImagePurpose.COVER, url))
                .isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void upload_shouldRejectSpoofedImageContent() {
        var file = new MockMultipartFile("file", "image.png", "image/png", "not an image".getBytes());
        assertThatThrownBy(() -> service.uploadManagedImage(1L, ImagePurpose.COVER, file))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
    }
}
