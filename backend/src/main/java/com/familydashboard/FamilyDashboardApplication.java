package com.familydashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.familydashboard.todo.HouseholdProperties;

@SpringBootApplication
@EnableConfigurationProperties(HouseholdProperties.class)
public class FamilyDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyDashboardApplication.class, args);
    }
}
