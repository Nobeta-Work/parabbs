package cn.nobeta.bbs.module.user.vo;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {

    private Long id;
    private String nickname;
    private String avatarUrl;
    private Integer sex;
    private String race;
    private String signature;
    private String backgroundImageUrl;
    private LocalDateTime createTime;

}
