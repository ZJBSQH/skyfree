package com.freesky.sprintbootsky.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * run_log 表实体。message 保存清洗后的整行日志（含代理前缀）。
 */
@Data
@TableName("run_log")
public class RunLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelProjectId;
    private Integer sequence;
    private String agent;
    private String message;

    private LocalDateTime createdAt;
}
