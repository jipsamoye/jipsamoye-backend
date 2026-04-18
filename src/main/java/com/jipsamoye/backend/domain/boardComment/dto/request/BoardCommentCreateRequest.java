package com.jipsamoye.backend.domain.boardComment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "자유게시판 댓글 작성 요청")
public record BoardCommentCreateRequest(

        @Schema(description = "댓글 내용", example = "좋은 글 감사합니다!")
        @NotBlank(message = "댓글 내용을 입력해주세요.")
        @Size(max = 500, message = "댓글은 500자 이하로 입력해주세요.")
        String content
) {
}
