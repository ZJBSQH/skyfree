package com.freesky.sprintbootsky.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * chapter 表实体。
 */
@Data
@TableName("chapter")
public class ChapterEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelProjectId;
    private Integer chapterIndex;
    private String content;

    private LocalDateTime createdAt;
}
