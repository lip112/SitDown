package com.univsitdown.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void 프로필_이미지_업로드를_위해_멀티파트_크기를_5MB로_제한한다() {
        contextRunner.run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.servlet.multipart.max-file-size"))
                    .isEqualTo("5MB");
            assertThat(context.getEnvironment().getProperty("spring.servlet.multipart.max-request-size"))
                    .isEqualTo("5MB");
        });
    }
}
