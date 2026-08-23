package com.freesky.sprintbootsky.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.freesky.sprintbootsky.domain.user.EmailAlreadyExistsException;
import com.freesky.sprintbootsky.domain.user.User;
import com.freesky.sprintbootsky.domain.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 MySQL 方言下的用户仓储集成测试（含邮箱唯一约束行为）。
 */
class MyBatisUserRepositoryIT extends AbstractMySqlIT {

    @Autowired
    private UserRepository userRepository;

    @Test
    void userCanBePersistedAndReadBack() {
        User saved = userRepository.save(new User("zheng", "zheng@example.com", "hash"));

        assertThat(saved.getId()).isNotNull();
        assertThat(userRepository.findById(saved.getId())).isPresent();
        assertThat(userRepository.findByEmail("zheng@example.com"))
                .isPresent()
                .get()
                .extracting(User::getUsername)
                .isEqualTo("zheng");
    }

    @Test
    void duplicateEmailViolatesUniqueConstraint() {
        userRepository.save(new User("first", "dup@example.com", "hash"));

        assertThatThrownBy(() -> userRepository.save(new User("second", "dup@example.com", "hash")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }
}
