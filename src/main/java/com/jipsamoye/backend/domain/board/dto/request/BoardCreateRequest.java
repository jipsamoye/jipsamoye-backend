package com.jipsamoye.backend.domain.board.dto.request;

import com.jipsamoye.backend.domain.board.entity.BoardCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "자유게시판 게시글 작성 요청")
public record BoardCreateRequest(

        @Schema(description = "카테고리 (GENERAL: 일반, QUESTION: 질문)", example = "GENERAL")
        @NotNull(message = "카테고리를 선택해주세요.")
        BoardCategory category,

        @Schema(description = "게시글 제목", example = "집사들 모여라!")
        @NotBlank(message = "제목을 입력해주세요.")
        @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
        String title,

        @Schema(description = "게시글 내용", example = "오늘 새로 입양한 고양이 자랑합니다.")
        @NotBlank(message = "내용을 입력해주세요.")
        @Size(max = 10000, message = "내용은 10000자 이하로 입력해주세요.")
        String content,

        @Schema(description = "이미지 URL 목록 (선택)", example = "[\"https://cdn.example.com/img1.jpg\"]")
        List<String> imageUrls
) {
}
