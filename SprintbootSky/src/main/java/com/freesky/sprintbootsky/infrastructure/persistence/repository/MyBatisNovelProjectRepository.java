package com.freesky.sprintbootsky.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.freesky.sprintbootsky.domain.novel.Chapter;
import com.freesky.sprintbootsky.domain.novel.CharacterProfile;
import com.freesky.sprintbootsky.domain.novel.CharacterRelationship;
import com.freesky.sprintbootsky.domain.novel.NovelProject;
import com.freesky.sprintbootsky.domain.novel.NovelProjectRepository;
import com.freesky.sprintbootsky.domain.novel.NovelProjectStatus;
import com.freesky.sprintbootsky.domain.novel.PlotOutlineItem;
import com.freesky.sprintbootsky.domain.novel.RunLog;
import com.freesky.sprintbootsky.domain.novel.TokenUsage;
import com.freesky.sprintbootsky.domain.novel.WorldSetting;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.ChapterEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.CharacterProfileEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.NovelProjectEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.RunLogEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.TokenUsageEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.ChapterMapper;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.CharacterProfileMapper;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.NovelProjectMapper;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.RunLogMapper;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.TokenUsageMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NovelProjectRepository 的 MyBatis-Plus 实现（Repository Adapter）。
 * 组合 5 个 Mapper 完成聚合整体持久化，隔离 MyBatis-Plus，业务层不直接依赖任何 Mapper；
 * JSON 字段统一经 ObjectMapper 序列化（禁止手工拼 JSON），序列化失败抛异常让短事务回滚。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MyBatisNovelProjectRepository implements NovelProjectRepository {

    private final NovelProjectMapper novelProjectMapper;
    private final ChapterMapper chapterMapper;
    private final CharacterProfileMapper characterProfileMapper;
    private final RunLogMapper runLogMapper;
    private final TokenUsageMapper tokenUsageMapper;
    private final ObjectMapper objectMapper;

    @Override
    public NovelProject begin(NovelProject project) {
        NovelProjectEntity entity = new NovelProjectEntity();
        entity.setUserId(project.getUserId());
        entity.setIdea(project.getIdea());
        entity.setTitle(project.getTitle());
        entity.setStatus(project.getStatus().name());
        entity.setReviewRound(0);
        novelProjectMapper.insert(entity);
        project.assignId(entity.getId());
        return project;
    }

    @Override
    public void saveCompleted(NovelProject project) {
        Long novelProjectId = project.getId();

        // 先更新项目主表为 COMPLETED（含 JSON 字段），再逐张子表插入；
        // 全部语句处于调用方（NovelTransactionService）的同一事务，任一失败整体回滚
        NovelProjectEntity projectEntity = new NovelProjectEntity();
        projectEntity.setId(novelProjectId);
        projectEntity.setTitle(project.getTitle());
        projectEntity.setStatus(project.getStatus().name());
        projectEntity.setReviewRound(project.getReviewRound());
        projectEntity.setWorldSettingsJson(writeJson(project.getWorldSettings()));
        projectEntity.setPlotOutlineJson(writeJson(project.getPlotOutline()));
        novelProjectMapper.updateById(projectEntity);

        for (Chapter chapter : project.getChapters()) {
            ChapterEntity chapterEntity = new ChapterEntity();
            chapterEntity.setNovelProjectId(novelProjectId);
            chapterEntity.setChapterIndex(chapter.chapterIndex());
            chapterEntity.setContent(chapter.content());
            chapterMapper.insert(chapterEntity);
        }

        for (CharacterProfile profile : project.getCharacters()) {
            CharacterProfileEntity profileEntity = new CharacterProfileEntity();
            profileEntity.setNovelProjectId(novelProjectId);
            profileEntity.setName(profile.name());
            profileEntity.setRoleType(profile.roleType());
            profileEntity.setAppearance(profile.appearance());
            profileEntity.setPersonality(profile.personality());
            profileEntity.setBackground(profile.background());
            profileEntity.setAbility(profile.ability());
            profileEntity.setMotivation(profile.motivation());
            profileEntity.setRelationshipsJson(writeJson(profile.relationships()));
            characterProfileMapper.insert(profileEntity);
        }

        for (RunLog logEntry : project.getLogs()) {
            RunLogEntity logEntity = new RunLogEntity();
            logEntity.setNovelProjectId(novelProjectId);
            logEntity.setSequence(logEntry.sequence());
            logEntity.setAgent(logEntry.agent());
            logEntity.setMessage(logEntry.message());
            runLogMapper.insert(logEntity);
        }

        if (project.getTokenUsage() != null) {
            TokenUsage usage = project.getTokenUsage();
            TokenUsageEntity usageEntity = new TokenUsageEntity();
            usageEntity.setNovelProjectId(novelProjectId);
            usageEntity.setInputTokens(usage.inputTokens());
            usageEntity.setOutputTokens(usage.outputTokens());
            usageEntity.setTotalTokens(usage.totalTokens());
            usageEntity.setCallCount(usage.callCount());
            usageEntity.setCostYuan(usage.costYuan());
            usageEntity.setModel(usage.model());
            tokenUsageMapper.insert(usageEntity);
        }
    }

    @Override
    public void saveFailedState(NovelProject project) {
        NovelProjectEntity entity = new NovelProjectEntity();
        entity.setId(project.getId());
        entity.setStatus(project.getStatus().name());
        entity.setErrorMessage(project.getErrorMessage());
        novelProjectMapper.updateById(entity);
    }

    @Override
    public Optional<NovelProject> findProjectState(Long novelProjectId) {
        NovelProjectEntity entity = novelProjectMapper.selectById(novelProjectId);
        if (entity == null) {
            return Optional.empty();
        }
        NovelProject project = NovelProject.reconstruct(
                entity.getId(), entity.getUserId(), entity.getIdea(), entity.getTitle(),
                NovelProjectStatus.valueOf(entity.getStatus()),
                entity.getReviewRound() == null ? 0 : entity.getReviewRound(),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, entity.getErrorMessage());
        return Optional.of(project);
    }

    @Override
    public Optional<NovelProject> findById(Long novelProjectId) {
        NovelProjectEntity entity = novelProjectMapper.selectById(novelProjectId);
        if (entity == null) {
            return Optional.empty();
        }

        List<Chapter> chapters = chapterMapper.selectList(
                        new LambdaQueryWrapper<ChapterEntity>()
                                .eq(ChapterEntity::getNovelProjectId, novelProjectId)
                                .orderByAsc(ChapterEntity::getChapterIndex))
                .stream()
                .map(chapter -> new Chapter(chapter.getChapterIndex(), chapter.getContent()))
                .toList();

        List<CharacterProfile> characters = characterProfileMapper.selectList(
                        new LambdaQueryWrapper<CharacterProfileEntity>()
                                .eq(CharacterProfileEntity::getNovelProjectId, novelProjectId)
                                .orderByAsc(CharacterProfileEntity::getId))
                .stream()
                .map(this::toCharacterProfile)
                .toList();

        List<RunLog> logs = runLogMapper.selectList(
                        new LambdaQueryWrapper<RunLogEntity>()
                                .eq(RunLogEntity::getNovelProjectId, novelProjectId)
                                .orderByAsc(RunLogEntity::getSequence))
                .stream()
                .map(logEntity -> new RunLog(logEntity.getSequence(), logEntity.getAgent(), logEntity.getMessage()))
                .toList();

        TokenUsage usage = null;
        TokenUsageEntity usageEntity = tokenUsageMapper.selectOne(
                new LambdaQueryWrapper<TokenUsageEntity>()
                        .eq(TokenUsageEntity::getNovelProjectId, novelProjectId)
                        .last("LIMIT 1"));
        if (usageEntity != null) {
            usage = new TokenUsage(
                    usageEntity.getInputTokens(), usageEntity.getOutputTokens(),
                    usageEntity.getTotalTokens(), usageEntity.getCallCount(),
                    usageEntity.getCostYuan(), usageEntity.getModel());
        }

        List<WorldSetting> worldSettings = readJsonList(
                entity.getWorldSettingsJson(), new TypeReference<>() { });
        List<PlotOutlineItem> plotOutline = readJsonList(
                entity.getPlotOutlineJson(), new TypeReference<>() { });

        return Optional.of(NovelProject.reconstruct(
                entity.getId(), entity.getUserId(), entity.getIdea(), entity.getTitle(),
                NovelProjectStatus.valueOf(entity.getStatus()),
                entity.getReviewRound() == null ? 0 : entity.getReviewRound(),
                worldSettings, plotOutline, characters, chapters, logs, usage, entity.getErrorMessage()));
    }

    private CharacterProfile toCharacterProfile(CharacterProfileEntity profileEntity) {
        return new CharacterProfile(
                profileEntity.getName(),
                profileEntity.getRoleType(),
                profileEntity.getAppearance(),
                profileEntity.getPersonality(),
                profileEntity.getBackground(),
                profileEntity.getAbility(),
                profileEntity.getMotivation(),
                readJsonList(profileEntity.getRelationshipsJson(), new TypeReference<>() { }));
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            // 序列化失败必须让对应 MySQL 短事务回滚
            throw new IllegalStateException("序列化小说 JSON 字段失败", exception);
        }
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JacksonException exception) {
            throw new IllegalStateException("解析小说 JSON 字段失败", exception);
        }
    }
}
