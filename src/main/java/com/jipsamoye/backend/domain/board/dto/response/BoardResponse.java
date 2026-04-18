package com.jipsamoye.backend.domain.board.dto.response;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.board.entity.BoardCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "자유게시판 게시글 상세 응답")
public record BoardResponse(

        @Schema(description = "게시글 ID")
        Long id,

        @Schema(description = "카테고리")
        BoardCategory category,

        @Schema(description = "제목")
        String title,

        @Schema(description = "내용")
        String content,

        @Schema(description = "이미지 URL 목록")
        List<String> imageUrls,

        @Schema(description = "조회수")
        int viewCount,

        @Schema(description = "댓글 수")
        int commentCount,

        @Schema(description = "좋아요 수")
        int likeCount,

        @Schema(description = "작성자 닉네임")
        String nickname,

        @Schema(description = "작성자 프로필 이미지 URL")
        String profileImageUrl,

        @Schema(description = "작성일시")
        LocalDateTime createdAt
) {
    public static BoardResponse from(Board board) {
        boolean isUserDeleted = board.getUser().isDeleted();
        return new BoardResponse(
                board.getId(),
                board.getCategory(),
                board.getTitle(),
                board.getContent(),
                board.getImageUrls(),
                board.getViewCount(),
                board.getCommentCount(),
                board.getLikeCount(),
                isUserDeleted ? "탈퇴한 사용자" : board.getUser().getNickname(),
                isUserDeleted ? null : board.getUser().getProfileImageUrl(),
                board.getCreatedAt()
        );
    }
}
