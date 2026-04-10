package com.jipsamoye.backend.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "프로필 수정 요청 (변경할 필드만 전송)")
public class UserUpdateRequest {

    @Schema(description = "닉네임 (2~10자, 한글/영문/숫자)", example = "멍집사")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9]{2,10}$", message = "닉네임은 2~10자의 한글, 영문, 숫자만 가능합니다.")
    private String nickname;

    @Schema(description = "소개글", example = "골든리트리버 집사입니다")
    @Size(max = 200, message = "소개글은 200자 이하로 입력해주세요.")
    private String bio;

    @Schema(description = "프로필 이미지 URL")
    private String profileImageUrl;
}
