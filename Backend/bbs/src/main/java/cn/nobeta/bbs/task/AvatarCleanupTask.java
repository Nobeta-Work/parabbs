package cn.nobeta.bbs.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.nobeta.bbs.module.file.service.FileService;

/**
 * 定时清理冗余图片文件
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AvatarCleanupTask {

    private final FileService fileService;

    @Scheduled(cron = "0 0 0/8 * * ?")
    public void cleanupExpiredAvatars() {
        log.info("===== 开始执行冗余图片清理任务 =====");
        fileService.cleanExpiredUnreferencedImages();
        log.info("===== 清理完成 =====");
    }

}
