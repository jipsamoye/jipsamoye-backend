package com.jipsamoye.backend.domain.notification.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeColumnLengthTest {

    @Test
    @DisplayName("NotificationType enum 값 이름이 컬럼 length를 초과하지 않는다")
    void notificationType_enum_이름_길이가_컬럼_길이를_초과하지_않는다() throws NoSuchFieldException {
        Field typeField = Notification.class.getDeclaredField("type");
        int columnLength = typeField.getAnnotation(Column.class).length();

        for (NotificationType type : NotificationType.values()) {
            assertThat(type.name().length())
                    .as("NotificationType.%s (%d자)이 컬럼 length(%d)을 초과", type.name(), type.name().length(), columnLength)
                    .isLessThanOrEqualTo(columnLength);
        }
    }
}
