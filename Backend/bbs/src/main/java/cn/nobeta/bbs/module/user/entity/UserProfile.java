package cn.nobeta.bbs.module.user.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Integer sex;
    private String race;
    private String signature;
    private String backgroundImageUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
