package com.jipsamoye.backend.domain.dm.dto.request;

import jakarta.validation.constraints.NotNull;

public record DmReadRequest(
        @NotNull(message = "roomId는 필수입니다.")
        Long roomId
) {
}
