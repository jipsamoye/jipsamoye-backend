package com.jipsamoye.backend.domain.board.controller;

import com.jipsamoye.backend.domain.board.dto.request.BoardCreateRequest;
import com.jipsamoye.backend.domain.board.dto.request.BoardUpdateRequest;
import com.jipsamoye.backend.domain.board.dto.response.BoardListResponse;
import com.jipsamoye.backend.domain.board.dto.response.BoardRecentResponse;
import com.jipsamoye.backend.domain.board.dto.response.BoardResponse;
import com.jipsamoye.backend.domain.board.entity.BoardCategory;
import com.jipsamoye.backend.domain.board.service.BoardService;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.response.ApiResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Board", description = "자유게시판 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boards")
public class BoardController {

    private final BoardService boardService;

    @Operation(summary = "최신 글 (메인 페이지용)", description = "자유게시판 최신 글 5개를 조회합니다.")
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<BoardRecentResponse>>> getRecentBoards() {
        return ResponseEntity.ok(ApiResponse.success(boardService.getRecentBoards()));
    }

    @Operation(summary = "게시글 작성", description = "자유게시판 게시글을 작성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<BoardResponse>> createBoard(
            @Valid @RequestBody BoardCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        BoardResponse response = boardService.createBoard(request, userDetails.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "게시글 목록 조회", description = "카테고리별 게시글 목록을 최신순으로 조회합니다. 카테고리 미입력 시 전체 조회.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BoardListResponse>>> getBoards(
            @Parameter(description = "카테고리 (GENERAL, QUESTION). 미입력 시 전체") @RequestParam(required = false) BoardCategory category,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        PageResponse<BoardListResponse> response = boardService.getBoards(category, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 상세 정보를 조회합니다. 조회 시 viewCount가 1 증가합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BoardResponse>> getBoard(
            @Parameter(description = "게시글 ID") @PathVariable Long id) {
        BoardResponse response = boardService.getBoard(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "게시글 수정", description = "본인 게시글만 수정할 수 있습니다.")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<BoardResponse>> updateBoard(
            @Parameter(description = "게시글 ID") @PathVariable Long id,
            @Valid @RequestBody BoardUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        BoardResponse response = boardService.updateBoard(id, request, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("게시글 수정 성공", response));
    }

    @Operation(summary = "게시글 삭제", description = "본인 게시글만 삭제할 수 있습니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBoard(
            @Parameter(description = "게시글 ID") @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.deleteBoard(id, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("게시글 삭제 성공"));
    }

    @Operation(summary = "게시글 검색", description = "제목 또는 제목+내용으로 게시글을 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<BoardListResponse>>> searchBoards(
            @Parameter(description = "검색 키워드") @RequestParam String q,
            @Parameter(description = "검색 타입 (TITLE: 제목, TITLE_CONTENT: 제목+내용)") @RequestParam(defaultValue = "TITLE_CONTENT") String type,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        PageResponse<BoardListResponse> response = boardService.searchBoards(q, type, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
