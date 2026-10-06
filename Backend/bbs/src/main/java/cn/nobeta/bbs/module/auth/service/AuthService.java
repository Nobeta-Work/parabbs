package cn.nobeta.bbs.module.auth.service;

import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;
import cn.nobeta.bbs.module.auth.vo.TokenVO;

public interface AuthService {

    TokenVO issue(UserAuthInfo loginUser);

    TokenVO refresh(String refreshToken);

    void logout(Long userId, String accessToken);

}
