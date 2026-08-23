package com.freesky.sprintbootsky.application.novel;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freesky.sprintbootsky.domain.novel.NovelProject;
import com.freesky.sprintbootsky.domain.novel.NovelProjectRepository;
import com.freesky.sprintbootsky.domain.novel.NovelProjectStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelTransactionServiceTest {

    @Mock
    private NovelProjectRepository novelProjectRepository;

    @InjectMocks
    private NovelTransactionService transactionService;

    @Test
    void startCreationReturnsNovelId() {
        NovelProject project = NovelProject.start(1L, "idea");
        when(novelProjectRepository.begin(project)).thenAnswer(invocation -> {
            NovelProject saved = invocation.getArgument(0);
            saved.assignId(7L);
            return saved;
        });

        Long novelId = transactionService.startCreation(project);

        assertThat(novelId).isEqualTo(7L);
        verify(novelProjectRepository).begin(project);
    }

    @Test
    void completeCreationDelegatesToRepository() {
        NovelProject project = NovelProject.start(1L, "idea");
        project.assignId(7L);
        project.complete(java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(),
                com.freesky.sprintbootsky.domain.novel.TokenUsage.zero(), 1);

        transactionService.completeCreation(project);

        verify(novelProjectRepository).saveCompleted(project);
    }

    @Test
    void markCreationFailedGoesThroughDomainTransition() {
        NovelProject state = NovelProject.start(1L, "idea");
        state.assignId(7L);
        when(novelProjectRepository.findProjectState(7L)).thenReturn(Optional.of(state));

        transactionService.markCreationFailed(7L, "安全错误信息");

        assertThat(state.getStatus()).isEqualTo(NovelProjectStatus.FAILED);
        assertThat(state.getErrorMessage()).isEqualTo("安全错误信息");
        verify(novelProjectRepository).saveFailedState(state);
    }

    @Test
    void markCreationFailedOnMissingProjectThrows() {
        when(novelProjectRepository.findProjectState(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.markCreationFailed(99L, "x"))
                .isInstanceOf(IllegalStateException.class);
        verify(novelProjectRepository, org.mockito.Mockito.never()).saveFailedState(any());
    }
}
