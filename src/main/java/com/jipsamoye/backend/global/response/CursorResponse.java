package com.jipsamoye.backend.global.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 커서(keyset) 기반 무한스크롤 응답 래퍼.
 * SliceResponse/PageResponse와 달리 offset/페이지 번호 없이 nextCursor로 다음 페이지를 요청한다.
 * COUNT 쿼리가 불필요하며, hasNext는 서비스 계층에서 size+1 조회로 판정한다.
 *
 * @param nextCursor 다음 요청에 전달할 마지막 항목 id. 끝이면 null.
 */
@Getter
public class CursorResponse<T> {

    private final List<T> content;
    private final int size;
    private final boolean hasNext;
    private final Long nextCursor;

    @Builder
    private CursorResponse(List<T> content, int size, boolean hasNext, Long nextCursor) {
        this.content = content;
        this.size = size;
        this.hasNext = hasNext;
        this.nextCursor = nextCursor;
    }

    public static <T> CursorResponse<T> of(List<T> content, int size, boolean hasNext, Long nextCursor) {
        return CursorResponse.<T>builder()
                .content(content)
                .size(size)
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .build();
    }
}
