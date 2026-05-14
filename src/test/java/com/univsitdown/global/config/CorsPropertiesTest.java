package com.univsitdown.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void 기본_CORS_오리진에_서비스_도메인을_포함한다() {
        contextRunner.run(context -> {
            String allowedOrigins = context.getEnvironment()
                    .getRequiredProperty("app.cors.allowed-origins");

            assertThat(Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .toList())
                    .contains(
                            "https://sitdown.bond",
                            "https://www.sitdown.bond",
                            "http://sitdown.bond",
                            "http://www.sitdown.bond"
                    );
        });
    }
}
