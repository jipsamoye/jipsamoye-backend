package com.jipsamoye.backend.domain.board.dto.response;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.board.entity.BoardCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "자유게시판 게시글 목록 응답")
public record BoardListResponse(

        @Schema(description = "게시글 ID")
        Long id,

        @Schema(description = "카테고리")
        BoardCategory category,

        @Schema(description = "제목")
        String title,

        @Schema(description = "내용 미리보기 (HTML 태그 제거 후 100자)")
        String contentPreview,

        @Schema(description = "댓글 수")
        int commentCount,

        @Schema(description = "조회수")
        int viewCount,

        @Schema(description = "좋아요 수")
        int likeCount,

        @Schema(description = "작성자 닉네임")
        String nickname,

        @Schema(description = "작성일시")
        LocalDateTime createdAt
) {
    public static BoardListResponse from(Board board) {
        String plainText = board.getContent().replaceAll("<[^>]*>", "");
        String preview = plainText.length() > 100 ? plainText.substring(0, 100) : plainText;
        boolean isUserDeleted = board.getUser().isDeleted();
        return new BoardListResponse(
                board.getId(),
                board.getCategory(),
                board.getTitle(),
                preview,
                board.getCommentCount(),
                board.getViewCount(),
                board.getLikeCount(),
                isUserDeleted ? "탈퇴한 사용자" : board.getUser().getNickname(),
                board.getCreatedAt()
        );
    }
}
