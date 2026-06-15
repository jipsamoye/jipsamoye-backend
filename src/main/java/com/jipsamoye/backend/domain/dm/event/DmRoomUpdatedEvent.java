package com.jipsamoye.backend.domain.dm.event;

import java.util.List;

public record DmRoomUpdatedEvent(
        Long roomId,
        List<Long> targetUserIds
) {
}
