package com.freesky.sprintbootsky.it;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Flyway 能在空 MySQL 数据库创建全部表、约束与索引。
 */
class FlywayMigrationIT extends AbstractMySqlIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SHOW TABLES", String.class);

        assertThat(tables).contains(
                "users", "novel_project", "chapter", "character_profile", "run_log", "token_usage");
    }

    @Test
    void usersEmailHasUniqueIndex() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'users' "
                        + "AND column_name = 'email' AND non_unique = 0",
                String.class);

        assertThat(indexes).contains("uk_users_email");
    }

    @Test
    void flywayHistoryRecordsSingleMigration() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);

        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
