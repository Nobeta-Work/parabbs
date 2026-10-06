package cn.nobeta.bbs.module.auth.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import cn.nobeta.bbs.module.user.entity.User;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {
        "authorities",
        "password",
        "username",
        "enabled",
        "accountNonExpired",
        "accountNonLocked",
        "credentialsNonExpired"
}, ignoreUnknown = true)
public class UserAuthInfo implements UserDetails {

    private User user;                // 数据库用户实体
    private List<String> permissions; // 权限码列表
    private List<String> roles;       // 角色码列表

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // 1. 封装角色
        if (roles != null && !roles.isEmpty()) {
            List<SimpleGrantedAuthority> roleAuth = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
            authorities.addAll(roleAuth);
        }

        // 2. 封装权限
        if (permissions != null && !permissions.isEmpty()) {
            List<SimpleGrantedAuthority> permAuth = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            authorities.addAll(permAuth);
        }

        return authorities;
    }

    @Override
    public String getPassword() {
        return ""; // BBS 不再保存或校验密码，仅保留 UserDetails 的业务身份接口。
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isEnabled() { return user.getStatus() == 1; }

    @Override
    public boolean isAccountNonLocked() { return user.getStatus() != 0; }


}
