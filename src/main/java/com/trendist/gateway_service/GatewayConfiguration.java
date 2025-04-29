package com.trendist.gateway_service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
public class GatewayConfiguration {

    private final JwtAuthorizationFilter jwtAuthorizationFilter;

    @Autowired
    public GatewayConfiguration(JwtAuthorizationFilter jwtAuthorizationFilter) {
        this.jwtAuthorizationFilter = jwtAuthorizationFilter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // User 서비스 라우트
                .route("user_service_route", r -> r.path("/users/**")
                        .filters(f -> f
                                // JWT 인증 필터
                                .filter(jwtAuthorizationFilter)
                                .removeRequestHeader(HttpHeaders.COOKIE)
                        )
                        .uri("lb://USER-SERVICE"))

                // 인증 필요 없는 라우트
                .route("user_public_route", r -> r.path("/users/public/**")
                        .filters(f -> f
                                        .removeRequestHeader(HttpHeaders.COOKIE)
                        )
                        .uri("lb://USER-SERVICE"))

                .build();
    }
}