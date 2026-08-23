package com.freesky.sprintbootsky.domain.novel;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NovelProjectTest {

    @Test
    void startCreatesProjectInCreatingState() {
        NovelProject project = NovelProject.start(1L, "  一座云上之城  ");

        assertThat(project.getStatus()).isEqualTo(NovelProjectStatus.CREATING);
        assertThat(project.getUserId()).isEqualTo(1L);
        assertThat(project.getTitle()).isEqualTo("一座云上之城");
        assertThat(project.getChapters()).isEmpty();
        assertThat(project.getTokenUsage()).isNull();
    }

    @Test
    void completeTransitionsToCompletedAndFillsData() {
        NovelProject project = NovelProject.start(1L, "idea");
        TokenUsage usage = new TokenUsage(10, 20, 30, 2, null, "deepseek-chat");

        project.complete(
                List.of(new WorldSetting("世界观", "k", "c", 1)),
                List.of(new PlotOutlineItem("1", "t", "s", List.of(), List.of(), List.of(), "主线", "铺垫")),
                List.of(new CharacterProfile("林风", "protagonist", "", "", "", "", "",
                        List.of(new CharacterRelationship("师父", "师徒", "变化")))),
                List.of(new Chapter(1, "第一章正文")),
                List.of(new RunLog(1, "WriterAgent", "[WriterAgent] Drafted chapter")),
                usage,
                2);

        assertThat(project.getStatus()).isEqualTo(NovelProjectStatus.COMPLETED);
        assertThat(project.getReviewRound()).isEqualTo(2);
        assertThat(project.getChapters()).hasSize(1);
        assertThat(project.getCharacters().get(0).name()).isEqualTo("林风");
        assertThat(project.getTokenUsage()).isSameAs(usage);
        assertThat(project.getErrorMessage()).isNull();
    }

    @Test
    void markFailedTransitionsToFailedWithSafeError() {
        NovelProject project = NovelProject.start(1L, "idea");

        project.markFailed("创作流程执行失败，请稍后重试");

        assertThat(project.getStatus()).isEqualTo(NovelProjectStatus.FAILED);
        assertThat(project.getErrorMessage()).isEqualTo("创作流程执行失败，请稍后重试");
    }

    @Test
    void stateTransitionsAreGuarded() {
        NovelProject project = NovelProject.start(1L, "idea");
        project.complete(List.of(), List.of(), List.of(), List.of(), List.of(), TokenUsage.zero(), 1);

        assertThatThrownBy(() -> project.complete(
                List.of(), List.of(), List.of(), List.of(), List.of(), TokenUsage.zero(), 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> project.markFailed("x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void assignIdCanOnlyHappenOnce() {
        NovelProject project = NovelProject.start(1L, "idea");
        project.assignId(5L);

        assertThat(project.getId()).isEqualTo(5L);
        assertThatThrownBy(() -> project.assignId(6L))
                .isInstanceOf(IllegalStateException.class);
    }
}
