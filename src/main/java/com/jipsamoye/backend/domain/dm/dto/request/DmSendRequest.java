package com.jipsamoye.backend.domain.dm.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record DmSendRequest(
        Long roomId,

        @Size(max = 50, message = "대상 닉네임이 너무 깁니다.")
        String targetNickname,

        @Size(max = 1000, message = "메시지는 1000자를 초과할 수 없습니다.")
        String content,

        @Size(max = 2048, message = "이미지 URL이 너무 깁니다.")
        String imageUrl,

        @Size(max = 100, message = "clientMessageId가 너무 깁니다.")
        String clientMessageId
) {
    /**
     * content와 imageUrl이 동시에 모두 비어 있으면(빈 메시지) 검증 실패시킨다.
     * 텍스트만, 이미지만, 둘 다 있는 메시지는 허용하되 빈 메시지는 차단한다.
     * 검증 메시지가 위반 필드("content")에 매핑되도록 메서드명을 {@code isContentValid}로 둔다.
     */
    @AssertTrue(message = "메시지 내용 또는 이미지가 있어야 합니다.")
    public boolean isContentValid() {
        boolean hasContent = content != null && !content.isBlank();
        boolean hasImage = imageUrl != null && !imageUrl.isBlank();
        return hasContent || hasImage;
    }
}
