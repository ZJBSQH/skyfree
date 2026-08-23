package com.freesky.sprintbootsky.application.port.out;

/**
 * JWT 签发端口。AuthApplicationService 只依赖本接口，
 * JWT 细节（密钥、算法、过期时间）封装在 infrastructure.security 的实现中。
 */
public interface TokenIssuer {

    String issue(Long userId, String email);
}
