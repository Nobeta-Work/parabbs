package cn.nobeta.bbs.module.file.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nobeta.bbs.config.FileConfig;
import cn.nobeta.bbs.module.file.entity.ImageFile;
import cn.nobeta.bbs.module.file.mapper.FileMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImageCleanupService {
    private final FileMapper fileMapper;
    private final FileConfig fileConfig;

    // 每张图片独立事务。设置图片与清理图片均先锁定同一文件记录。
    @Transactional(rollbackFor = IOException.class)
    public boolean clean(Long id, LocalDateTime now) throws IOException {
        ImageFile image = fileMapper.selectByIdForUpdate(id);
        if (image == null || image.getExpireTime() == null || image.getExpireTime().isAfter(now)
            || fileMapper.isReferenced(image.getPublicUrl())) {
            return false;
        }
        Path root = Path.of(fileConfig.getManaged().getRootPath()).toAbsolutePath().normalize();
        Path target = root.resolve(image.getStorageKey()).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("图片存储键超出配置目录");
        }
        Files.deleteIfExists(target);
        fileMapper.deleteImage(id);
        return true;
    }
}
