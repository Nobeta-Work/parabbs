package cn.nobeta.bbs.module.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import cn.nobeta.bbs.common.enums.RedisKeys;
import cn.nobeta.bbs.module.auth.dto.LoginDTO;
import cn.nobeta.bbs.module.auth.dto.RegisterDTO;
import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;
import cn.nobeta.bbs.module.auth.mapper.AuthMapper;
import cn.nobeta.bbs.module.auth.service.impl.AuthServiceImpl;
import cn.nobeta.bbs.module.auth.vo.TokenVO;
import cn.nobeta.bbs.module.user.entity.User;
import cn.nobeta.bbs.module.user.mapper.UserMapper;
import cn.nobeta.bbs.security.util.TokenProvider;
import cn.nobeta.bbs.support.TestDataFactory;

/**
 * ? knowledge
 * JUnit 5 用于整合 Mockito 单元测试框架的扩展注解
 * 核心作用：启用 Mockito 注解，不启动 Spring 容器
 * - @Mock 标注字段：自动生成对应的 Mock 代理对象(假对象，默认所有方法返回 null/0/false)
 * - @Spy 标注字段：自动被 Mockito 包装；未被打桩方法执行真实逻辑，打桩方法模拟逻辑
 * - @InjectMocks：被测类示例，自动将 @Mock/@Spy 对象注入
 * 打桩：提前给假对象定义“调用时，返回什么/做什么”
 * 验证：执行完被测方法，检查假对象的方法是否正确调用
 */
@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private UserMapper userMapper;


    @Captor
    private ArgumentCaptor<User> userCaptor;

    // @Test
    void whenParamValid_loginOK() {
        // 构造 dto
        LoginDTO dto = TestDataFactory.loginDTO();

        // ===== 委托 AuthenticationManager 登陆认证 =====

        // 1. 如果登陆成功，构造 Authentication
        UserAuthInfo loginUser = TestDataFactory.loginUser();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            loginUser,
            null,
            loginUser.getAuthorities()
        );

        // 2. 打桩：委托登陆认证
        when(authenticationManager.authenticate(any(Authentication.class)))
            .thenReturn(authentication);

        // 3. 打桩：TokenProvider
        Date expireIn = new Date(System.currentTimeMillis() + 1800_000L);
        when(tokenProvider.generateAccessToken(loginUser))
            .thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(loginUser))
            .thenReturn("refresh-token");
        when(tokenProvider.getExpiration("access-token"))
            .thenReturn(expireIn);

        // 4. 打桩：RedisTemplate
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);


        // act
        TokenVO vo = authService.login(dto);

        // 断言：vo 结果
        assertEquals(vo.getAccessToken(), "access-token");
        assertEquals(vo.getRefreshToken(), "refresh-token");
        assertEquals(vo.getExpireIn(), expireIn);

        // 验证并捕获：获取传入 authentication
        ArgumentCaptor<Authentication> authCaptor = 
            ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authCaptor.capture());

        // 断言：authentication 的认证信息填充
        assertEquals(authCaptor.getValue().getPrincipal(), "alice");
        assertEquals(authCaptor.getValue().getCredentials(), "raw-pwd");


        // 验证：Redis 方法执行
        Duration ttl = Duration.ofSeconds(RedisKeys.REFRESH_TOKEN.getDefaultTtl());

        verify(valueOperations).set(
            RedisKeys.LOGIN_USER.getFullKey(1L), 
            loginUser, 
            ttl
        );

        verify(valueOperations).set(
            RedisKeys.REFRESH_TOKEN.getFullKey(1L),
            "refresh-token",
            ttl
        );

    }


    @Test
    void whenUsernameAvailable_registerOK() {
        // 构造 dto
        RegisterDTO dto = TestDataFactory.registerDTO();

        // 打桩：密码加密
        when(passwordEncoder.encode(dto.getPassword())).thenReturn("encoded-pwd");

        // 打桩：插入用户 (insertUser 后模拟数据库自增主键回填 User)
        doAnswer(invocation -> {
            invocation.<User>getArgument(0).setId(1L);
            return null;
        }).when(authMapper).insertUser(any(User.class));

        // 打桩：插入默认角色
        when(authMapper.insertUserDefaultRole(1L)).thenReturn(1);

        authService.register(dto);

        // 验证并捕获：验证插入用户方法被调用，并捕获传入的参数
        verify(authMapper).insertUser(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        // 断言验证：校验 savedUser
        assertNotNull(savedUser);
        assertEquals(savedUser.getUsername(), dto.getUsername());
        assertEquals(savedUser.getPassword(), "encoded-pwd");
        assertEquals(savedUser.getId(), 1L);

        ArgumentCaptor<cn.nobeta.bbs.module.user.entity.UserProfile> profileCaptor =
            ArgumentCaptor.forClass(cn.nobeta.bbs.module.user.entity.UserProfile.class);
        verify(userMapper).insertUserProfile(profileCaptor.capture());
        assertEquals(profileCaptor.getValue().getUserId(), 1L);
        assertEquals(profileCaptor.getValue().getNickname(), dto.getNickname());

        // 验证：调用插入默认角色方法，且参数为 1
        verify(authMapper).insertUserDefaultRole(1L);
    }
}
