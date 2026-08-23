package com.freesky.sprintbootsky.interfaces.novel;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freesky.sprintbootsky.application.novel.NovelApplicationService;
import com.freesky.sprintbootsky.application.novel.NovelCreationResult;
import com.freesky.sprintbootsky.interfaces.novel.dto.CreateNovelRequest;
import com.freesky.sprintbootsky.interfaces.novel.dto.CreateNovelResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 小说接口。需要 JWT；Controller 只负责 HTTP 输入输出，
 * userId 从认证 principal（Long）直接取得并传给 Application Service。
 */
@RestController
@RequestMapping("/api/novels")
@RequiredArgsConstructor
public class NovelController {

    private final NovelApplicationService novelApplicationService;

    @PostMapping
    public ResponseEntity<CreateNovelResponse> createNovel(
            @Valid @RequestBody CreateNovelRequest request,
            @AuthenticationPrincipal Long userId) {
        NovelCreationResult result = novelApplicationService.createNovel(userId, request.idea());
        HttpStatus status = result.success() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(CreateNovelResponse.from(result));
    }
}
