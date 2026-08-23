package com.freesky.sprintbootsky.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * novel_project 表实体。复杂 JSON 字段以 TEXT 落库，由 Repository Adapter 负责序列化。
 */
@Data
@TableName("novel_project")
public class NovelProjectEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String idea;
    private String title;
    /** CREATING / COMPLETED / FAILED */
    private String status;
    private Integer reviewRound;
    private String worldSettingsJson;
    private String plotOutlineJson;
    private String errorMessage;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
