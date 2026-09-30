package cn.nobeta.bbs.module.admin.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    /** 状态 0:封禁 1:正常 */
    private Integer status;
    private LocalDateTime createTime;
    /** 用户持有的角色编码列表 */
    private List<String> roleCodes;

}
