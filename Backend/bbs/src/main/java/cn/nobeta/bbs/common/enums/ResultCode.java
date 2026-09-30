package cn.nobeta.bbs.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "服务器异常"),
    
    // =========== 登陆认证模块 ===========
    UNAUTHORIZED(401, "未登陆，请先授权"),
    FORBIDDEN(403, "权限不足"),
    USERNAME_NOT_FOUND(400, "用户名或密码错误"),
    PASSWORD_NOT_MATCHES(400, "用户名或密码错误"), 
    ACCOUNT_FORBBIDEN(403, "该账户已被封禁"),
    RESOURCE_NOT_FOUND(404, "该资源为空"),
    OLD_PASSWORD_ERROR(400, "密码错误"),
    NEW_PASSWORD_SAME_AS_OLD(400, "新旧密码不能相同"),
    USERNAME_DUPLICATE(400, "用户名重复"),
    NICKNAME_DUPLICATE(400, "昵称重复"),
    ILLEGAL_ARGUMENT(400, "参数校验异常"),
    
    // =========== 文件模块 ===========
    FILE_ERROR(500, "文件传输失败"),
    FILE_TYPE_ERROR(400, "文件类型错误"),
    FILE_IS_NULL(400, "文件为空"),
    FILE_OUT_SIZE(400, "文件过大"),
    
    // =========== 博客模块 ===========
    BLOG_TITLE_DUPLICATE(400, "同目录下存在同名博客"), 
    COMMENT_NOT_FOUND(404, "评论不存在"),
    COMMENT_PARENT_INVALID(400, "父评论信息异常"),
    
    // =========== 目录模块 ===========
    FOLDER_NOT_FOUND(404, "目录不存在"), 
    FOLDER_TOO_DEEP(400, "目录深度过高"), 
    FOLDER_NAME_DUPLICATE(400, "同目录下存在同名子目录"), 
    FOLDER_NAME_NOT_CHANGED(400, "目录名未改变"), 
    PATH_NOT_AVALIABLE(400, "路径异常"), 
    FOLDER_NOT_CHANGED(400, "无效改变"), 
    FOLDER_OPERAION_NOT_ALLOWED(400, "操作异常"), 
    
    // =========== 标签模块 ===========
    TAG_NAME_DUPLICATE(400, "标签名重复"), 
    
    // =========== Agent 模块 ===========
    AGENT_NAME_DUPLICATE(400, "Token 名重复"),
    
    // =========== 后台管理模块 ===========
    ADMIN_FORBIDDEN(403, "无后台管理权限"),
    USER_NOT_FOUND(404, "用户不存在"),
    ROLE_NOT_FOUND(404, "角色不存在"),
    PERM_NOT_FOUND(404, "权限不存在"),
    BLOG_NOT_FOUND(404, "博客不存在"),
    TAG_NOT_FOUND(404, "标签不存在"),
    ROLE_CODE_DUPLICATE(400, "角色编码重复"),
    ROLE_NAME_DUPLICATE(400, "角色名重复"),
    PERM_CODE_DUPLICATE(400, "权限编码重复"),
    PERM_NAME_DUPLICATE(400, "权限名重复"),
    ROLE_PERM_BIND_FAILED(400, "角色权限绑定失败"),
    USER_ROLE_BIND_FAILED(400, "用户角色绑定失败"), 
    
    // =========== 接口限流 ===========
    TOO_MANY_REQUESTS(429, "访问频繁，请稍后重试"),
    
    ;

    

    private final Integer code;
    private final String msg;

}
