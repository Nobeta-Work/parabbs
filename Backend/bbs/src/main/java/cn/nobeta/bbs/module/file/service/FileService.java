package cn.nobeta.bbs.module.file.service;

import org.springframework.web.multipart.MultipartFile;
import cn.nobeta.bbs.module.file.entity.ImagePurpose;

public interface FileService {
    String uploadManagedImage(Long userId, ImagePurpose purpose, MultipartFile file);
    /** 调用方业务事务中锁定图片，防止与清理任务并发；null 表示不设置图片。 */
    void validateManagedImage(Long userId, ImagePurpose purpose, String url);
    int cleanExpiredUnreferencedImages();
    String uploadImage(MultipartFile file);
}
