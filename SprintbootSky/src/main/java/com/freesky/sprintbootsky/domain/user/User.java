package com.freesky.sprintbootsky.domain.user;

import lombok.Getter;

/**
 * 用户领域模型（聚合根）。
 * 只暴露读取方法；身份 ID 由持久化层回填，业务层不直接修改内部状态。
 */
@Getter
public class User {

    private Long id;
    private final String username;
    private final String email;
    private final String passwordHash;

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /** 插入数据库后由 Repository 回填自增主键；只允许赋值一次，防止破坏身份一致性。 */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("用户 ID 已分配，不允许重复赋值");
        }
        this.id = id;
    }
}
