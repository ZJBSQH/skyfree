package com.freesky.sprintbootsky.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * MySQL 集成测试基类（类名以 IT 结尾，默认 mvn test 不运行，需 -Pit-mysql）。
 * 默认通过 Testcontainers 启动 MySQL 8 容器（需要 Docker）；
 * 无 Docker 环境可改用本地 MySQL（会真实执行 Flyway 迁移，请使用专用测试库）：
 * <pre>
 *   -Dfreesky.it.mysql.url=jdbc:mysql://127.0.0.1:3306/freesky_it?createDatabaseIfNotExist=true
 *   -Dfreesky.it.mysql.username=root
 *   -Dfreesky.it.mysql.password=...
 * </pre>
 */
@SpringBootTest(properties = {
        // 集成测试使用独立测试密钥，不读取开发者本机敏感变量
        "security.jwt.secret=it-test-secret-0123456789abcdef0123456789abcdef0123456789abcdef"})
public abstract class AbstractMySqlIT {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("freesky_it");

    static {
        if (!hasLocalMySqlOverride()) {
            try {
                MYSQL.start();
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "启动 Testcontainers MySQL 失败：需要 Docker，或通过 -Dfreesky.it.mysql.url 指定本地 MySQL",
                        exception);
            }
        }
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        if (hasLocalMySqlOverride()) {
            registry.add("spring.datasource.url", () -> System.getProperty("freesky.it.mysql.url"));
            registry.add("spring.datasource.username", () -> System.getProperty("freesky.it.mysql.username", "root"));
            registry.add("spring.datasource.password", () -> System.getProperty("freesky.it.mysql.password", ""));
        } else {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        }
    }

    private static boolean hasLocalMySqlOverride() {
        String url = System.getProperty("freesky.it.mysql.url");
        return url != null && !url.isBlank();
    }
}
