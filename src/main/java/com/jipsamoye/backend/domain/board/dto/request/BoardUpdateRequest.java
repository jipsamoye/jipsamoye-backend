package com.jipsamoye.backend.domain.board.dto.request;

import com.jipsamoye.backend.domain.board.entity.BoardCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "자유게시판 게시글 수정 요청 (변경할 필드만 전송)")
public record BoardUpdateRequest(

        @Schema(description = "카테고리 (GENERAL: 일반, QUESTION: 질문)", example = "QUESTION")
        BoardCategory category,

        @Schema(description = "게시글 제목", example = "수정된 제목입니다.")
        @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
        String title,

        @Schema(description = "게시글 내용", example = "수정된 내용입니다.")
        @Size(max = 10000, message = "내용은 10000자 이하로 입력해주세요.")
        String content,

        @Schema(description = "이미지 URL 목록", example = "[\"https://cdn.example.com/img1.jpg\"]")
        List<String> imageUrls
) {
}
