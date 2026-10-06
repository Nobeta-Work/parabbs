package cn.nobeta.bbs.module.user.entity;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** BBS 业务用户；密码及统一身份由 Auth 管理。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "社区业务用户")
public class User {
    private Long id;
    private String username;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
