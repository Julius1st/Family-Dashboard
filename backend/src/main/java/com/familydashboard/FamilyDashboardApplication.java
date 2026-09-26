package com.familydashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.familydashboard.todo.HouseholdProperties;
import com.familydashboard.weather.WeatherProperties;

@SpringBootApplication
@EnableConfigurationProperties({HouseholdProperties.class, WeatherProperties.class})
@EnableScheduling
public class FamilyDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyDashboardApplication.class, args);
    }
}
