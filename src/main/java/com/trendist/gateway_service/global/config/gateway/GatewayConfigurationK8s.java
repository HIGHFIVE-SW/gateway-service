package com.trendist.gateway_service.global.config.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;

import com.trendist.gateway_service.global.filter.jwt.JwtAuthorizationFilter;

@Configuration
@Profile("prod")
public class GatewayConfigurationK8s {
	private final JwtAuthorizationFilter jwtAuthorizationFilter;

	@Autowired
	public GatewayConfigurationK8s(JwtAuthorizationFilter jwtAuthorizationFilter) {
		this.jwtAuthorizationFilter = jwtAuthorizationFilter;
	}

	@Bean
	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes()
			.route("oauth2_login_start_route", r -> r.path("/oauth2/**")
				.filters(f -> f
					.removeRequestHeader(HttpHeaders.COOKIE))
				.uri("lb://user-service")
			)

			.route("oauth2_callback_route", r -> r.path("/login/oauth2/code/**")
				.uri("lb://user-service")
			)

			// User 서비스 라우트
			.route("user_service_route", r -> r.path("/users/**")
				.filters(f -> f
					// JWT 인증 필터
					.filter(jwtAuthorizationFilter)
					.removeRequestHeader(HttpHeaders.COOKIE)
				)
				.uri("lb://user-service"))

			.route("post_service_route", r -> r.path("/posts/**", "/comments/**",
					"/reviews/**", "/profile/reviews/**", "/profile/posts/**", "/presignedurls/**")
				.filters(f -> f
					.filter(jwtAuthorizationFilter)
					.removeRequestHeader(HttpHeaders.COOKIE)
				)
				.uri("lb://post-service")
			)

			.route("issue_service_route", r -> r.path("/issues/**", "/profile/issues/**")
				.filters(f -> f
					// JWT 인증 필터
					.filter(jwtAuthorizationFilter)
					.removeRequestHeader(HttpHeaders.COOKIE)
				)
				.uri("lb://issue-service"))

			// Activity 서비스  라우트 설정
			.route("activity-service_route", r -> r.path("/activities/**",
					"/profile/activities/**")
				.filters(f -> f
					.filter(jwtAuthorizationFilter)
					.removeRequestHeader(HttpHeaders.COOKIE)
				)
				.uri("lb://activity-service")
			)

			// 인증 필요 없는 라우트
			.route("user_public_route", r -> r.path("/users/public/**")
				.filters(f -> f
					.removeRequestHeader(HttpHeaders.COOKIE)
				)
				.uri("lb://user-service"))

			.route("user_api_docs", r -> r.path("/api-docs/users/**")
				.filters(f -> f
					// /api-docs/users/v3/api-docs → /v3/api-docs
					.rewritePath("/api-docs/users/(?<rem>.*)", "/${rem}")
				)
				.uri("lb://user-service"))

			.route("post_api_docs", r -> r.path("/api-docs/posts/**")
				.filters(f -> f
					.rewritePath("/api-docs/posts/(?<rem>.*)", "/${rem}")
				)
				.uri("lb://post-service"))

			.route("issue_api_docs", r -> r.path("/api-docs/issues/**")
				.filters(f -> f
					.rewritePath("/api-docs/issues/(?<rem>.*)", "/${rem}")
				)
				.uri("lb://issue-service"))

			.route("activity_api_docs", r -> r.path("/api-docs/activities/**")
				.filters(f -> f
					.rewritePath("/api-docs/activities/(?<rem>.*)", "/${rem}")
				)
				.uri("lb://activity-service"))

			.build();
	}
}
