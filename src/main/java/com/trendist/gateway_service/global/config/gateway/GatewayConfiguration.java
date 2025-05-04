package com.trendist.gateway_service.global.config.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import com.trendist.gateway_service.global.filter.jwt.JwtAuthorizationFilter;

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

			.route("post_service_route", r -> r.path("/posts/**")
				.filters(f -> f
					.filter(jwtAuthorizationFilter)
					.removeRequestHeader(HttpHeaders.COOKIE)
				)
				.uri("lb://POST-SERVICE")
			)

			// 인증 필요 없는 라우트
			.route("user_public_route", r -> r.path("/users/public/**")
				.filters(f -> f
					.removeRequestHeader(HttpHeaders.COOKIE)
				)
				.uri("lb://USER-SERVICE"))

			.build();
	}
}
