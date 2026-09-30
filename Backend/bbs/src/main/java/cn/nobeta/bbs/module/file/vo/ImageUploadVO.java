package cn.nobeta.bbs.module.file.vo;

import cn.nobeta.bbs.module.file.entity.ImagePurpose;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageUploadVO {
    private String url;
    private ImagePurpose purpose;
}
