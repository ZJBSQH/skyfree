package com.freesky.sprintbootsky.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * character_profile 表实体。relationships 以 JSON 文本保存。
 */
@Data
@TableName("character_profile")
public class CharacterProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelProjectId;
    private String name;
    private String roleType;
    private String appearance;
    private String personality;
    private String background;
    private String ability;
    private String motivation;
    private String relationshipsJson;

    private LocalDateTime createdAt;
}
