package com.jipsamoye.backend.domain.dm.dto.response;

public record DmMessageEvent(
        String type,
        DmMessagePayload message
) {
    public static DmMessageEvent of(DmMessageResponse r, String clientMessageId) {
        return new DmMessageEvent("MESSAGE", DmMessagePayload.of(r, clientMessageId));
    }
}
