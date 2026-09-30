package cn.nobeta.bbs.module.user.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileDTO {
    @NotBlank
    @Size(min = 1, max = 10)
    private String nickname;
    @NotNull
    @Min(0)
    @Max(2)
    private Integer sex;
    @NotNull
    @Size(max = 10)
    private String race;
    @NotNull
    @Size(max = 255)
    private String signature;
    @Size(max = 2048)
    private String backgroundImageUrl;

    // 必需构造参数区分缺少字段与显式 null，不增加兼容状态。
    @JsonCreator
    public UserProfileDTO(
        @JsonProperty(value = "nickname", required = true) String nickname,
        @JsonProperty(value = "sex", required = true) Integer sex,
        @JsonProperty(value = "race", required = true) String race,
        @JsonProperty(value = "signature", required = true) String signature,
        @JsonProperty(value = "backgroundImageUrl", required = true) String backgroundImageUrl
    ) {
        this.nickname = nickname;
        this.sex = sex;
        this.race = race;
        this.signature = signature;
        this.backgroundImageUrl = backgroundImageUrl;
    }
}
