package com.qznu.jobhelper.common.web;

/**
 * 一次请求内的登录用户快照。
 *
 * <p>role 和 status 均来自数据库实时查询，不直接信任 JWT 中的 role，
 * 这样管理员改角色或禁用账号后能立即生效。</p>
 */
public record UserContextData(Long userId, String username, String nickname,
                              String role, Integer status, String ip) {
}
