package com.jipsamoye.backend.domain.chat.dto.request;

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

@DisplayName("ChatSendRequest 검증")
class ChatSendRequestValidationTest {

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
    @DisplayName("정상 내용은 통과")
    void valid() {
        assertThat(validator.validate(new ChatSendRequest("안녕하세요"))).isEmpty();
    }

    @Test
    @DisplayName("null content → NotBlank 위반")
    void nullContent_invalid() {
        Set<ConstraintViolation<ChatSendRequest>> violations = validator.validate(new ChatSendRequest(null));
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("content"));
    }

    @Test
    @DisplayName("공백 content → NotBlank 위반")
    void blankContent_invalid() {
        Set<ConstraintViolation<ChatSendRequest>> violations = validator.validate(new ChatSendRequest("   "));
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("content"));
    }

    @Test
    @DisplayName("1000자 초과 content → Size 위반")
    void tooLongContent_invalid() {
        Set<ConstraintViolation<ChatSendRequest>> violations =
                validator.validate(new ChatSendRequest("a".repeat(1001)));
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("content"));
    }
}
