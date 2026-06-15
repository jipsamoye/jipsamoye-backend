package com.jipsamoye.backend.loadtest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 더미 데이터 TSV 를 실제로 생성하는 일회성 실행기.
 *
 * <p>1000만 행(pet_post.tsv 약 3GB)을 디스크에 쓰므로 일반 빌드(`./gradlew build`)에서는
 * 돌지 않도록 {@link Disabled}.
 * 적재가 필요할 때만 아래 중 하나로 실행한다:
 * <pre>
 *   # (A) Gradle 로 이 테스트만 강제 실행
 *   ./gradlew test --tests '*DummyDataGeneratorRunTest' -Dloadtest.run=true
 *
 *   # (B) main() 직접 실행 (run.sh 가 사용하는 방식)
 *   java -cp build/classes/java/test:build/classes/java/main \
 *        com.jipsamoye.backend.loadtest.DummyDataGenerator loadtest/data
 * </pre>
 */
class DummyDataGeneratorRunTest {

    @Test
    @Disabled("1000만 행 파일 생성용 일회성 실행기. run.sh 또는 -Dloadtest.run=true 로만 실행")
    @DisplayName("users/pet_post TSV 생성 (loadtest/data)")
    void generateAll() throws IOException {
        Path dir = Path.of("loadtest/data");
        java.nio.file.Files.createDirectories(dir);
        DummyDataGenerator.generateUsers(
                dir.resolve("users.tsv"), DummyDataGenerator.USER_COUNT, 20260614L);
        DummyDataGenerator.generatePosts(
                dir.resolve("pet_post.tsv"),
                DummyDataGenerator.POST_COUNT, DummyDataGenerator.USER_COUNT, 20260615L);
    }
}
