package com.freesky.sprintbootsky.domain.user;

import java.util.Optional;

/**
 * 用户仓储接口（领域端口）。Application 只依赖此接口，不感知 MyBatis-Plus。
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findByEmail(String email);

    Optional<User> findById(Long id);
}
