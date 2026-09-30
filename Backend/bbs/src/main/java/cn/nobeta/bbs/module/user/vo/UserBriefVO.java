package cn.nobeta.bbs.module.user.vo;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefVO {

    private Long id;
    private String nickname;
    private String avatarUrl;

}
