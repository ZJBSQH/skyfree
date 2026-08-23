package com.freesky.sprintbootsky.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * users 表实体。仅供 Repository Adapter 与 Mapper 使用，禁止泄漏到 Application/Domain。
 */
@Data
@TableName("users")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String email;
    private String passwordHash;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
