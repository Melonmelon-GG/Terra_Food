package com.dayan.food;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SessionConfigurationTests {

    @Test
    void defaultsToSevenDaysForSessionAndCookie() throws IOException {
        MockEnvironment environment = applicationEnvironment();
        Binder binder = Binder.get(environment);

        assertThat(binder.bind("spring.session.timeout", Duration.class).get())
                .isEqualTo(Duration.ofDays(7));
        assertThat(binder.bind("server.servlet.session.cookie.max-age", Duration.class).get())
                .isEqualTo(Duration.ofDays(7));
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only", Boolean.class))
                .isTrue();
    }

    @Test
    void allowsOverridingTimeoutAndDisablingPersistentCookie() throws IOException {
        MockEnvironment environment = applicationEnvironment()
                .withProperty("SESSION_TIMEOUT", "30m")
                .withProperty("SESSION_COOKIE_MAX_AGE", "-1s");
        Binder binder = Binder.get(environment);

        assertThat(binder.bind("spring.session.timeout", Duration.class).get())
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(binder.bind("server.servlet.session.cookie.max-age", Duration.class).get())
                .isEqualTo(Duration.ofSeconds(-1));
    }

    private MockEnvironment applicationEnvironment() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        // Read the shared defaults without starting database/Redis connections or inheriting local overrides.
        environment.getPropertySources().addLast(new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).getFirst());
        return environment;
    }
}
