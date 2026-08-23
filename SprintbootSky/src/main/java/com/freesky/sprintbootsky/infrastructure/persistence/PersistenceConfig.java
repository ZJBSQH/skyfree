package com.freesky.sprintbootsky.infrastructure.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus Mapper 扫描配置。
 * 独立于主应用类：WebMvc 切片测试不会加载本配置，避免在无 SqlSessionFactory 的切片里注册 Mapper。
 */
@Configuration
@MapperScan("com.freesky.sprintbootsky.infrastructure.persistence.mapper")
public class PersistenceConfig {
}
