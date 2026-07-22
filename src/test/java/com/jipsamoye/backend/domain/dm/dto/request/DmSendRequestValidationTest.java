package com.jipsamoye.backend.domain.dm.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmSendRequest 검증")
class DmSendRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("content만 있어도 통과")
    void contentOnly_valid() {
        DmSendRequest request = new DmSendRequest(1L, null, "안녕", null, "cid");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("imageUrl만 있어도 통과 (이미지 전용 메시지)")
    void imageOnly_valid() {
        DmSendRequest request = new DmSendRequest(1L, null, null, "https://img/x.jpg", "cid");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("content·imageUrl 동시 부재 → contentValid 위반")
    void bothMissing_invalid() {
        DmSendRequest request = new DmSendRequest(1L, null, "   ", null, "cid");
        Set<ConstraintViolation<DmSendRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("contentValid"));
    }

    @Test
    @DisplayName("content가 1000자 초과면 위반")
    void contentTooLong_invalid() {
        String tooLong = "a".repeat(1001);
        DmSendRequest request = new DmSendRequest(1L, null, tooLong, null, "cid");
        Set<ConstraintViolation<DmSendRequest>> violations = validator.validate(request);
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("content"));
    }

    @Test
    @DisplayName("imageUrl이 2048자 초과면 위반")
    void imageUrlTooLong_invalid() {
        String tooLong = "https://" + "a".repeat(2048);
        DmSendRequest request = new DmSendRequest(1L, null, null, tooLong, "cid");
        Set<ConstraintViolation<DmSendRequest>> violations = validator.validate(request);
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("imageUrl"));
    }

    @Test
    @DisplayName("clientMessageId가 100자 초과면 위반")
    void clientMessageIdTooLong_invalid() {
        String tooLong = "c".repeat(101);
        DmSendRequest request = new DmSendRequest(1L, null, "안녕", null, tooLong);
        Set<ConstraintViolation<DmSendRequest>> violations = validator.validate(request);
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("clientMessageId"));
    }
}
