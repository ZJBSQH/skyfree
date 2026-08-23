package com.freesky.sprintbootsky.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * token_usage 表实体。成本字段使用 BigDecimal 映射 DECIMAL(12,4)。
 */
@Data
@TableName("token_usage")
public class TokenUsageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelProjectId;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long callCount;
    private BigDecimal costYuan;
    private String model;

    private LocalDateTime createdAt;
}
