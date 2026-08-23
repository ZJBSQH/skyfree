package com.freesky.sprintbootsky.it;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
import com.freesky.sprintbootsky.domain.user.User;
import com.freesky.sprintbootsky.domain.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MySQL 方言下的聚合仓储集成测试：JSON 字段、多表写入、聚合回读。
 */
class MyBatisNovelProjectRepositoryIT extends AbstractMySqlIT {

    @Autowired
    private NovelProjectRepository repository;

    @Autowired
    private UserRepository userRepository;

    /** 新建真实用户以满足 novel_project.user_id 外键约束。 */
    private Long newUserId() {
        return userRepository.save(new User("zheng", "repo-" + System.nanoTime() + "@example.com", "hash")).getId();
    }

    @Test
    void aggregateRoundTripsThroughMysql() {
        Long userId = newUserId();
        NovelProject project = NovelProject.start(userId, "一座云上之城");
        repository.begin(project);
        Long novelId = project.getId();

        // 初始 CREATING 状态可轻量读回
        Optional<NovelProject> state = repository.findProjectState(novelId);
        assertThat(state).isPresent();
        assertThat(state.get().getStatus()).isEqualTo(NovelProjectStatus.CREATING);

        project.complete(
                List.of(new WorldSetting("世界观", "k1", "云上之城", 2)),
                List.of(new PlotOutlineItem("n1", "序章", "摘要", List.of("伏笔1"),
                        List.of("林风"), List.of("k1"), "主线", "铺垫")),
                List.of(new CharacterProfile("林风", "protagonist", "白衣", "坚韧", "孤儿",
                        "剑术", "复仇", List.of(new CharacterRelationship("师父", "师徒", "逐渐疏远")))),
                List.of(new Chapter(1, "第一章正文"), new Chapter(2, "第二章正文")),
                List.of(new RunLog(1, "WriterAgent", "[WriterAgent] Drafted chapter"),
                        new RunLog(2, "ReviewerAgent", "[ReviewerAgent] 审核通过")),
                new TokenUsage(8000, 4450, 12450, 8, new BigDecimal("0.0169"), "deepseek-chat"),
                1);
        repository.saveCompleted(project);

        NovelProject loaded = repository.findById(novelId).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(NovelProjectStatus.COMPLETED);
        assertThat(loaded.getReviewRound()).isEqualTo(1);
        assertThat(loaded.getTitle()).isEqualTo("一座云上之城");

        // 章节按序号有序
        assertThat(loaded.getChapters()).extracting(Chapter::chapterIndex).containsExactly(1, 2);
        assertThat(loaded.getChapters().get(0).content()).isEqualTo("第一章正文");

        // 人物卡与 JSON 关系字段
        CharacterProfile character = loaded.getCharacters().get(0);
        assertThat(character.name()).isEqualTo("林风");
        assertThat(character.relationships()).containsExactly(
                new CharacterRelationship("师父", "师徒", "逐渐疏远"));

        // 世界观 / 大纲 JSON 往返
        assertThat(loaded.getWorldSettings()).containsExactly(
                new WorldSetting("世界观", "k1", "云上之城", 2));
        assertThat(loaded.getPlotOutline().get(0).tensionLevel()).isEqualTo("铺垫");
        assertThat(loaded.getPlotOutline().get(0).foreshadowing()).containsExactly("伏笔1");

        // 日志按序号有序
        assertThat(loaded.getLogs()).extracting(RunLog::agent)
                .containsExactly("WriterAgent", "ReviewerAgent");

        // Token 用量
        assertThat(loaded.getTokenUsage().totalTokens()).isEqualTo(12450);
        assertThat(loaded.getTokenUsage().costYuan()).isEqualByComparingTo("0.0169");
    }

    @Test
    void failedStateIsPersistedWithSafeError() {
        Long userId = newUserId();
        NovelProject project = NovelProject.start(userId, "灵感");
        repository.begin(project);
        Long novelId = project.getId();

        project.markFailed("创作流程执行失败，请稍后重试");
        repository.saveFailedState(project);

        NovelProject state = repository.findProjectState(novelId).orElseThrow();
        assertThat(state.getStatus()).isEqualTo(NovelProjectStatus.FAILED);
        assertThat(state.getErrorMessage()).isEqualTo("创作流程执行失败，请稍后重试");
    }
}
