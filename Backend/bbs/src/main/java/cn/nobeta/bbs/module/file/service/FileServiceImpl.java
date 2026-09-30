package cn.nobeta.bbs.module.file.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.common.util.GithubFileUtil;
import cn.nobeta.bbs.config.FileConfig;
import cn.nobeta.bbs.module.file.entity.ImageFile;
import cn.nobeta.bbs.module.file.entity.ImagePurpose;
import cn.nobeta.bbs.module.file.mapper.FileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    private static final Map<String, String> EXTENSIONS = Map.of(
        "image/jpeg", ".jpg", "image/jpg", ".jpg", "image/png", ".png",
        "image/gif", ".gif", "image/webp", ".webp");
    private final FileConfig fileConfig;
    private final FileMapper fileMapper;
    private final GithubFileUtil githubFileUtil;
    private final ImageCleanupService cleanupService;
    private final Environment environment;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String uploadManagedImage(Long userId, ImagePurpose purpose, MultipartFile file) {
        if (userId == null || purpose == null) {
            throw new BusinessException(ResultCode.ILLEGAL_ARGUMENT);
        }
        long limit = purpose == ImagePurpose.AVATAR ? fileConfig.getManaged().getAvatarMaxSize()
            : fileConfig.getManaged().getMaxSize();
        validateFile(file, limit);
        LocalDate now = LocalDate.now();
        String key = (purpose == ImagePurpose.AVATAR ? "" : purpose.name().toLowerCase(java.util.Locale.ROOT) + "/") + now.getYear() + "/"
            + now.getMonthValue() + "/" + now.getDayOfMonth() + "/"
            + UUID.randomUUID().toString().replace("-", "") + EXTENSIONS.get(file.getContentType());
        String base = fileConfig.getManaged().getPublicBaseUrl();
        String url = (base.endsWith("/") ? base : base + "/") + key;
        validateUrl(url);
        Path target = Path.of(fileConfig.getManaged().getRootPath()).toAbsolutePath().normalize().resolve(key);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            fileMapper.insertImage(ImageFile.builder().storageKey(key).publicUrl(url).userId(userId)
                .purpose(purpose).expireTime(LocalDateTime.now().plusHours(fileConfig.getManaged().getExpire()))
                .build());
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                log.warn("Failed to remove incomplete image, storageKey={}", key);
            }
            throw new BusinessException(ResultCode.FILE_ERROR);
        }
        return url;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void validateManagedImage(Long userId, ImagePurpose purpose, String url) {
        if (url == null) return;
        validateUrl(url);
        ImageFile image = fileMapper.selectByUrlForUpdate(url);
        if (image == null || !userId.equals(image.getUserId()) || image.getPurpose() != purpose) {
            throw new BusinessException(ResultCode.ILLEGAL_ARGUMENT, "图片必须来自本人对应用途的上传记录");
        }
        Path root = Path.of(fileConfig.getManaged().getRootPath()).toAbsolutePath().normalize();
        Path target = root.resolve(image.getStorageKey()).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new BusinessException(ResultCode.ILLEGAL_ARGUMENT, "图片文件不存在");
        }
    }

    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            boolean localHttp = environment.acceptsProfiles(Profiles.of("dev"))
                && "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                    || "[::1]".equals(uri.getHost()));
            if (url.length() > 2048 || (!https && !localHttp) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.ILLEGAL_ARGUMENT, "图片必须使用长期有效的完整 HTTPS URL，开发环境允许本地 HTTP");
        }
    }

    @Override
    public int cleanExpiredUnreferencedImages() {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (Long id : fileMapper.selectExpiredImageIds(now)) {
            try {
                if (cleanupService.clean(id, now)) count++;
            } catch (IOException | RuntimeException e) {
                log.warn("Failed to clean image, imageId={}", id);
            }
        }
        return count;
    }

    @Override
    public String uploadImage(MultipartFile file) {
        validateFile(file, fileConfig.getImage().getMaxSize());
        try {
            return githubFileUtil.upload(file);
        } catch (IOException e) {
            log.error("Image upload failed");
            throw new BusinessException(ResultCode.FILE_ERROR);
        }
    }

    private void validateFile(MultipartFile file, long maxSize) {
        if (file == null || file.isEmpty()) throw new BusinessException(ResultCode.FILE_IS_NULL);
        if (file.getSize() > maxSize) throw new BusinessException(ResultCode.FILE_OUT_SIZE);
        if (!EXTENSIONS.containsKey(file.getContentType() == null ? "" : file.getContentType())
            || !fileConfig.getImage().getAcceptTypes().contains(file.getContentType())) {
            throw new BusinessException(ResultCode.FILE_TYPE_ERROR);
        }
        try (java.io.InputStream stream = file.getInputStream()) {
            byte[] header = stream.readNBytes(12);
            boolean valid = switch (file.getContentType()) {
                case "image/jpeg", "image/jpg" -> header.length >= 3
                    && (header[0] & 255) == 255 && (header[1] & 255) == 216 && (header[2] & 255) == 255;
                case "image/png" -> header.length >= 8 && java.util.Arrays.equals(
                    java.util.Arrays.copyOf(header, 8), new byte[] {(byte)137, 80, 78, 71, 13, 10, 26, 10});
                case "image/gif" -> header.length >= 6 && (ascii(header, 0, 6).equals("GIF87a")
                    || ascii(header, 0, 6).equals("GIF89a"));
                case "image/webp" -> header.length >= 12 && ascii(header, 0, 4).equals("RIFF")
                    && ascii(header, 8, 4).equals("WEBP");
                default -> false;
            };
            if (!valid) throw new BusinessException(ResultCode.FILE_TYPE_ERROR);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_ERROR);
        }
    }

    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
