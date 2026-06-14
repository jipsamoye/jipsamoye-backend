package com.jipsamoye.backend.global.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * 무한스크롤(Slice) 전용 응답 래퍼.
 * PageResponse와 달리 totalPages/totalElements가 없어 COUNT 쿼리가 불필요하다.
 * hasNext는 Spring Data가 size+1 조회로 판정한다.
 */
@Getter
public class SliceResponse<T> {

    private final List<T> content;
    private final int currentPage;
    private final int size;
    private final boolean hasNext;

    @Builder
    private SliceResponse(List<T> content, int currentPage, int size, boolean hasNext) {
        this.content = content;
        this.currentPage = currentPage;
        this.size = size;
        this.hasNext = hasNext;
    }

    public static <T> SliceResponse<T> from(Slice<T> slice) {
        return SliceResponse.<T>builder()
                .content(slice.getContent())
                .currentPage(slice.getNumber())
                .size(slice.getSize())
                .hasNext(slice.hasNext())
                .build();
    }
}
