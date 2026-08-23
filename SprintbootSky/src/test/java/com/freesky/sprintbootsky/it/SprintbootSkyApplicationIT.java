package com.freesky.sprintbootsky.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证应用能在空 MySQL 上完整启动：Flyway 完成迁移、MyBatis 装配成功、上下文可用。
 */
class SprintbootSkyApplicationIT extends AbstractMySqlIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsWithFlywayMigratedSchema() {
        Integer userTables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('users','novel_project','chapter','character_profile','run_log','token_usage')",
                Integer.class);

        assertThat(userTables).isEqualTo(6);
    }
}
