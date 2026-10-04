package cn.nobeta.auth.module.client;

import java.util.List;
import org.apache.ibatis.annotations.*;

/** Queries identifiers only; all serialized protocol records are read/written by Spring JDBC. */
@Mapper
public interface ProtocolMapper {
    @Select("SELECT id FROM oauth2_registered_client WHERE id = #{id} FOR UPDATE")
    String lockClient(String id);

    @Select({"<script>", "SELECT id FROM oauth2_registered_client WHERE 1=1",
            "<if test='query != null'> AND (client_id LIKE CONCAT('%', #{query}, '%') OR client_name LIKE CONCAT('%', #{query}, '%')) </if>",
            "<if test='enabled != null'> AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(client_settings, '$.\"para.client.enabled\"')), 'true') = #{enabled} </if>",
            "ORDER BY client_id LIMIT #{limit} OFFSET #{offset}", "</script>"})
    List<String> pageClients(@Param("query") String query, @Param("enabled") String enabled,
                            @Param("limit") int limit, @Param("offset") long offset);

    @Select({"<script>", "SELECT COUNT(*) FROM oauth2_registered_client WHERE 1=1",
            "<if test='query != null'> AND (client_id LIKE CONCAT('%', #{query}, '%') OR client_name LIKE CONCAT('%', #{query}, '%')) </if>",
            "<if test='enabled != null'> AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(client_settings, '$.\"para.client.enabled\"')), 'true') = #{enabled} </if>",
            "</script>"})
    long countClients(@Param("query") String query, @Param("enabled") String enabled);

    @Select("SELECT id FROM oauth2_authorization WHERE registered_client_id = #{id}")
    List<String> authorizationsByClient(String id);

    @Select("SELECT id FROM oauth2_authorization WHERE principal_name = #{subject}")
    List<String> authorizationsBySubject(String subject);

    @Select("SELECT registered_client_id FROM oauth2_authorization_consent WHERE principal_name = #{subject}")
    List<String> consentClients(String subject);
}
