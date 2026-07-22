package com.jipsamoye.backend.domain.figurine.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class FigurineAsyncConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FigurineAsyncConfig.class)
            .withPropertyValues(
                    "openai.api-key=test-key",
                    "openai.model=gpt-image-1",
                    "openai.size=1024x1024",
                    "openai.quality=medium");

    @Test
    @DisplayName("openai 프로퍼티가 OpenAiProperties 빈에 바인딩된다")
    void bindsOpenAiProperties() {
        contextRunner.run(context -> {
            OpenAiProperties props = context.getBean(OpenAiProperties.class);
            assertThat(props.apiKey()).isEqualTo("test-key");
            assertThat(props.model()).isEqualTo("gpt-image-1");
            assertThat(props.size()).isEqualTo("1024x1024");
            assertThat(props.quality()).isEqualTo("medium");
        });
    }

    @Test
    @DisplayName("figurineExecutor 빈이 core 1 / max 2 / queue 20으로 생성된다")
    void createsFigurineExecutor() {
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean("figurineExecutor", ThreadPoolTaskExecutor.class);
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getQueueCapacity()).isEqualTo(20);
        });
    }
}
