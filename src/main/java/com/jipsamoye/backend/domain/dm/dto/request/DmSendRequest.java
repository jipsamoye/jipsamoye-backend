package com.jipsamoye.backend.domain.dm.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DmSendRequest {
    private Long userId;
    private Long roomId;
    private String content;
    private String imageUrl;
}
