package cn.nobeta.bbs.module.file.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.nobeta.bbs.config.FileConfig;
import cn.nobeta.bbs.module.file.entity.ImageFile;
import cn.nobeta.bbs.module.file.mapper.FileMapper;

class ImageCleanupServiceTest {
    @TempDir Path root;

    @Test
    void clean_shouldKeepFile_whenAnyBusinessReferenceExists() throws Exception {
        FileMapper mapper = mock(FileMapper.class);
        FileConfig config = config();
        Path file = Files.write(root.resolve("used.png"), new byte[] {1});
        when(mapper.selectByIdForUpdate(1L)).thenReturn(image("used.png"));
        when(mapper.isReferenced("https://example.com/used.png")).thenReturn(true);
        assertThat(new ImageCleanupService(mapper, config).clean(1L, LocalDateTime.now())).isFalse();
        assertThat(file).exists();
        verify(mapper, never()).deleteImage(anyLong());
    }

    @Test
    void clean_shouldRemoveExpiredUnreferencedFileAndRecord() throws Exception {
        FileMapper mapper = mock(FileMapper.class);
        Path file = Files.write(root.resolve("unused.png"), new byte[] {1});
        when(mapper.selectByIdForUpdate(1L)).thenReturn(image("unused.png"));
        assertThat(new ImageCleanupService(mapper, config()).clean(1L, LocalDateTime.now())).isTrue();
        assertThat(file).doesNotExist();
        verify(mapper).deleteImage(1L);
    }

    @Test
    void clean_shouldRejectStorageKeyOutsideRoot() {
        FileMapper mapper = mock(FileMapper.class);
        when(mapper.selectByIdForUpdate(1L)).thenReturn(image("../outside.png"));
        assertThatThrownBy(() -> new ImageCleanupService(mapper, config()).clean(1L, LocalDateTime.now()))
            .isInstanceOf(java.io.IOException.class);
        verify(mapper, never()).deleteImage(anyLong());
    }

    private FileConfig config() {
        FileConfig config = new FileConfig();
        config.getManaged().setRootPath(root.toString());
        return config;
    }
    private ImageFile image(String key) {
        return ImageFile.builder().id(1L).storageKey(key).publicUrl("https://example.com/" + key)
            .expireTime(LocalDateTime.now().minusHours(1)).build();
    }
}
