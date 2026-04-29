package com.jipsamoye.backend.domain.boardComment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "자유게시판 댓글/답글 작성 요청")
public record BoardCommentCreateRequest(

        @Schema(description = "게시글 ID", example = "1")
        @NotNull(message = "게시글 ID를 입력해주세요.")
        Long boardId,

        @Schema(description = "부모 댓글 ID (답글 시 입력, 부모 댓글이면 null)")
        Long parentId,

        @Schema(description = "멘션할 유저 ID (답글의 답글 시 자동 설정)")
        Long mentionedUserId,

        @Schema(description = "댓글 내용", example = "좋은 글 감사합니다!")
        @NotBlank(message = "댓글 내용을 입력해주세요.")
        @Size(max = 500, message = "댓글은 500자 이하로 입력해주세요.")
        String content
) {
}
