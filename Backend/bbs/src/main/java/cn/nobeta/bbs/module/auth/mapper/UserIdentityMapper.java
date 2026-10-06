package cn.nobeta.bbs.module.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import cn.nobeta.bbs.module.user.entity.User;

@Mapper
public interface UserIdentityMapper {
    User findUser(@Param("issuer") String issuer, @Param("subject") String subject);
    int insert(@Param("userId") Long userId, @Param("issuer") String issuer, @Param("subject") String subject);
}
