package com.freesky.sprintbootsky.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freesky.sprintbootsky.domain.user.EmailAlreadyExistsException;
import com.freesky.sprintbootsky.domain.user.User;
import com.freesky.sprintbootsky.domain.user.UserRepository;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.UserEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * UserRepository 的 MyBatis-Plus 实现（Repository Adapter）。
 * 把持久化实体与领域模型相互转换，Application/Domain 不直接接触 Mapper。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisUserRepository implements UserRepository {

    private final UserMapper userMapper;

    @Override
    public User save(User user) {
        UserEntity entity = new UserEntity();
        entity.setUsername(user.getUsername());
        entity.setEmail(user.getEmail());
        entity.setPasswordHash(user.getPasswordHash());
        try {
            userMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            // 并发重复注册：users.email 唯一约束触发时转换为领域异常，HTTP 层统一返回 409
            throw new EmailAlreadyExistsException(user.getEmail());
        }
        user.assignId(entity.getId());
        return user;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        UserEntity entity = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDomain);
    }

    private User toDomain(UserEntity entity) {
        User user = new User(entity.getUsername(), entity.getEmail(), entity.getPasswordHash());
        user.assignId(entity.getId());
        return user;
    }
}
