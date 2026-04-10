package com.jipsamoye.backend.global.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final int totalPages;
    private final long totalElements;
    private final int currentPage;
    private final int size;
    private final boolean hasNext;

    @Builder
    private PageResponse(List<T> content, int totalPages, long totalElements,
                         int currentPage, int size, boolean hasNext) {
        this.content = content;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.currentPage = currentPage;
        this.size = size;
        this.hasNext = hasNext;
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .currentPage(page.getNumber())
                .size(page.getSize())
                .hasNext(page.hasNext())
                .build();
    }
}
