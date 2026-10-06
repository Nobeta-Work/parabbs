package cn.nobeta.auth.module.account;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
    @Select("SELECT * FROM auth_account WHERE username = #{username}")
    Account findByUsername(String username);

    @Select("SELECT * FROM auth_account WHERE subject = #{subject}")
    Account findBySubject(String subject);

    @Select("SELECT * FROM auth_account WHERE id = #{id}")
    Account findById(long id);

    @Select("SELECT role_code FROM auth_account_role WHERE account_id = #{accountId} ORDER BY role_code")
    List<String> roles(long accountId);

    @Insert("INSERT INTO auth_account(subject, username, password_hash, status) VALUES(#{subject}, #{username}, #{passwordHash}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Account account);

    @Update("UPDATE auth_account SET password_hash = #{hash}, update_time = UTC_TIMESTAMP(6) WHERE id = #{id} AND password_hash = #{previousHash}")
    int updatePassword(@Param("id") long id, @Param("hash") String hash, @Param("previousHash") String previousHash);

    @Update("UPDATE auth_account SET status = #{status}, update_time = UTC_TIMESTAMP(6) WHERE id = #{id}")
    void updateStatus(@Param("id") long id, @Param("status") int status);

    @Insert("INSERT INTO auth_account_role(account_id, role_code) VALUES(#{id}, #{role})")
    void insertRole(@Param("id") long id, @Param("role") String role);

    @Delete("DELETE FROM auth_account_role WHERE account_id = #{id}")
    void deleteRoles(long id);

    @Select("SELECT account_id FROM auth_account_role WHERE role_code = 'ROLE_AUTH_ADMIN' ORDER BY account_id FOR UPDATE")
    List<Long> lockAdministrators();

    @Select("SELECT COUNT(*) FROM auth_account a JOIN auth_account_role r ON r.account_id = a.id WHERE r.role_code = 'ROLE_AUTH_ADMIN' AND a.status = 1")
    long enabledAdministratorCount();

    @Select("SELECT * FROM auth_account WHERE id = #{id} FOR UPDATE")
    Account lockAccount(long id);

    @Select("<script>SELECT * FROM auth_account <where><if test='query != null'>LOCATE(#{query}, username) &gt; 0</if><if test='status != null'>AND status = #{status}</if></where> ORDER BY id LIMIT #{limit} OFFSET #{offset}</script>")
    List<Account> search(@Param("query") String query, @Param("status") Integer status,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM auth_account <where><if test='query != null'>LOCATE(#{query}, username) &gt; 0</if><if test='status != null'>AND status = #{status}</if></where></script>")
    long searchCount(@Param("query") String query, @Param("status") Integer status);

    @Select("SELECT * FROM auth_account ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<Account> page(@Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM auth_account")
    long count();
}
