package cn.nobeta.bbs.module.file.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import cn.nobeta.bbs.module.file.entity.ImageFile;

@Mapper
public interface FileMapper {
    void insertImage(ImageFile file);
    ImageFile selectByUrlForUpdate(@Param("url") String url);
    ImageFile selectByIdForUpdate(@Param("id") Long id);
    List<Long> selectExpiredImageIds(@Param("now") LocalDateTime now);
    boolean isReferenced(@Param("url") String url);
    void deleteImage(@Param("id") Long id);
}
