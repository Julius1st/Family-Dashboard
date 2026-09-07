package com.familydashboard.todo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves {@link HouseholdProperties} actually binds {@code household.members}
 * from {@code application.yml} at runtime, not just that it compiles.
 */
@SpringBootTest
class HouseholdPropertiesTest {

    @Autowired
    private HouseholdProperties householdProperties;

    @Test
    void bindsMembersFromApplicationYml() {
        assertThat(householdProperties.members()).containsExactly("Alice", "Bob");
    }
}
