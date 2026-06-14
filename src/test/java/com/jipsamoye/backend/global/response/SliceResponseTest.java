package com.jipsamoye.backend.global.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SliceResponseTest {

    @Test
    @DisplayName("Slice의 content·currentPage·size·hasNext를 그대로 옮긴다")
    void from_mapsSliceFields() {
        Slice<String> slice = new SliceImpl<>(List.of("a", "b"), PageRequest.of(2, 10), true);

        SliceResponse<String> response = SliceResponse.from(slice);

        assertThat(response.getContent()).containsExactly("a", "b");
        assertThat(response.getCurrentPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.isHasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 페이지(빈 Slice)는 hasNext가 false다")
    void from_emptySlice_hasNextFalse() {
        Slice<String> slice = new SliceImpl<>(List.of(), PageRequest.of(0, 20), false);

        SliceResponse<String> response = SliceResponse.from(slice);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getCurrentPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
    }
}
