package cn.nobeta.bbs.module.file.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageFile {
    private Long id;
    private String storageKey;
    private String publicUrl;
    private Long userId;
    private ImagePurpose purpose;
    private LocalDateTime expireTime;
}
