package com.dayan.food;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_AUDIT_BUSINESS_MYSQL_TESTS", matches = "true")
class DayanFoodApplicationTests {
    @Autowired TestRestTemplate http;

    @Test
    void actualServerAcceptsPublicRequestsAndRejectsAnonymousPrivateRequests() {
        var catalog = http.getForEntity("/api/foods/catalog?page=1&pageSize=1", String.class);
        assertThat(catalog.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(catalog.getBody()).contains("\"items\"", "\"total\"");
        var identity = http.getForEntity("/api/auth/me", String.class);
        assertThat(identity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(identity.getBody()).contains("AUTHENTICATION_REQUIRED");
    }
}
