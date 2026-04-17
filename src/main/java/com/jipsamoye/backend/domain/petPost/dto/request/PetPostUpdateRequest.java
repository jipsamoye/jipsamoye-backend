package com.jipsamoye.backend.domain.petPost.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "게시글 수정 요청 (변경할 필드만 전송)")
public record PetPostUpdateRequest(

        @Schema(description = "게시글 제목", example = "수정된 제목")
        @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
        String title,

        @Schema(description = "게시글 내용", example = "수정된 내용")
        @Size(max = 5000, message = "내용은 5000자 이하로 입력해주세요.")
        String content,

        @Schema(description = "이미지 URL 목록 (1~5장)")
        @Size(min = 1, max = 5, message = "이미지는 1장 이상 5장 이하로 업로드할 수 있습니다.")
        List<String> imageUrls
) {
}
