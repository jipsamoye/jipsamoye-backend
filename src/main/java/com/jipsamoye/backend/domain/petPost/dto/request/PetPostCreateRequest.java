package com.jipsamoye.backend.domain.petPost.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "게시글 작성 요청")
public class PetPostCreateRequest {

    @Schema(description = "게시글 제목", example = "우리 강아지 자랑!")
    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
    private String title;

    @Schema(description = "게시글 내용", example = "오늘 산책하고 왔어요~")
    @NotBlank(message = "내용을 입력해주세요.")
    @Size(max = 5000, message = "내용은 5000자 이하로 입력해주세요.")
    private String content;

    @Schema(description = "이미지 URL 목록 (1~5장)", example = "[\"https://s3.amazonaws.com/img1.jpg\"]")
    @NotEmpty(message = "이미지를 1장 이상 업로드해주세요.")
    @Size(max = 5, message = "이미지는 최대 5장까지 업로드할 수 있습니다.")
    private List<String> imageUrls;
}
