package com.jipsamoye.backend.domain.board.dto.response;

import com.jipsamoye.backend.domain.board.entity.Board;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "자유게시판 최신 글 응답 (메인 페이지용)")
public record BoardRecentResponse(

        @Schema(description = "게시글 ID")
        Long id,

        @Schema(description = "제목")
        String title,

        @Schema(description = "작성일시")
        LocalDateTime createdAt
) {
    public static BoardRecentResponse from(Board board) {
        return new BoardRecentResponse(
                board.getId(),
                board.getTitle(),
                board.getCreatedAt()
        );
    }
}
