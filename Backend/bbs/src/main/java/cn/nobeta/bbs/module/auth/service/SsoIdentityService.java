package cn.nobeta.bbs.module.auth.service;

import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;
import cn.nobeta.bbs.module.auth.mapper.AuthMapper;
import cn.nobeta.bbs.module.auth.mapper.UserIdentityMapper;
import cn.nobeta.bbs.module.user.entity.User;
import cn.nobeta.bbs.module.user.entity.UserProfile;
import cn.nobeta.bbs.module.user.mapper.UserMapper;

/** 仅接收框架已经验证的 OIDC 身份，权限始终来自 BBS。 */
@Service
public class SsoIdentityService {
    private final UserIdentityMapper identities;
    private final AuthMapper auth;
    private final UserMapper users;
    private final TransactionTemplate transaction;

    public SsoIdentityService(UserIdentityMapper identities, AuthMapper auth, UserMapper users,
            PlatformTransactionManager transactions) {
        this.identities = identities;
        this.auth = auth;
        this.users = users;
        this.transaction = new TransactionTemplate(transactions);
    }

    public UserAuthInfo resolve(String issuer, String subject) {
        if (issuer == null || issuer.length() > 255 || subject == null || subject.isBlank() || subject.length() > 255) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "认证身份不符合关联规则");
        }
        User user = identities.findUser(issuer, subject);
        for (int attempt = 0; user == null && attempt < 3; attempt++) {
            try {
                user = transaction.execute(status -> {
                    User existing = identities.findUser(issuer, subject);
                    if (existing != null) return existing;
                    // openid 不保证用户名或昵称；生成 BBS 自己的唯一标识，不按同名账号绑定。
                    String suffix = UUID.randomUUID().toString().replace("-", "");
                    User created = User.builder().username("bbs_" + suffix).status(1).build();
                    auth.insertUser(created);
                    users.insertUserProfile(UserProfile.builder().userId(created.getId())
                            .nickname("用户" + suffix.substring(0, 8)).sex(2).race("").signature("").build());
                    if (auth.insertUserDefaultRole(created.getId()) != 1) {
                        throw new BusinessException(ResultCode.FAIL, "默认社区角色不存在");
                    }
                    identities.insert(created.getId(), issuer, subject);
                    return created;
                });
            } catch (DuplicateKeyException conflict) {
                // 唯一约束处理同时首次登录；失败事务整体回滚后重新读取获胜的关联。
                user = identities.findUser(issuer, subject);
            }
        }
        if (user == null) throw new BusinessException(ResultCode.FAIL, "创建社区用户失败，请重新登录");
        return load(user.getId());
    }

    public UserAuthInfo load(Long userId) {
        User user = users.selectUserById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "社区用户不存在或已被封禁");
        }
        return new UserAuthInfo(user, auth.selectPermCodesByUserId(userId), auth.selectRoleCodesByUserId(userId));
    }
}
