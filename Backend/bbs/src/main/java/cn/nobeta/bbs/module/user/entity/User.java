package cn.nobeta.bbs.module.user.entity;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** sys_user 仅包含认证信息，不承载公开资料。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "用户认证实体")
public class User {
    private Long id;
    private String username;
    @Schema(description = "密码哈希")
    private String password;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
