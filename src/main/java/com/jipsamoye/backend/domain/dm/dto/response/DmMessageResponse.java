package com.jipsamoye.backend.domain.dm.dto.response;

import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.global.util.ImageCdnConverter;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DmMessageResponse {

    private Long id;
    private Long senderId;
    private String senderNickname;
    private String content;
    private String imageUrl;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static DmMessageResponse from(DmMessage message, ImageCdnConverter converter) {
        return DmMessageResponse.builder()
                .id(message.getId())
                .senderId(message.getSender().getId())
                .senderNickname(message.getSender().getNickname())
                .content(message.getContent())
                .imageUrl(converter.toCdnUrl(message.getImageUrl()))
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
