package com.trendist.gateway_service.global.config.health;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.health.StatusAggregator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class HealthCheckConfiguration {

	private static final Logger LOG = LoggerFactory.getLogger(HealthCheckConfiguration.class);

	private final StatusAggregator statusAggregator;
	private final WebClient.Builder webClientBuilder;
	private WebClient webClient;

	@Autowired
	public HealthCheckConfiguration(WebClient.Builder webClientBuilder, StatusAggregator statusAggregator) {
		this.webClientBuilder = webClientBuilder;
		this.statusAggregator = statusAggregator;
	}

	@Bean
	ReactiveHealthIndicator serviceHealthCheck() {
		return () -> {
			Map<String, Mono<Health>> healthChecks = new HashMap<>();

			// 각 서비스에 대한 health indicator 추가
			healthChecks.put("user-service", getHealth("http://user-service"));
			healthChecks.put("post-service", getHealth("http://post-service"));
			healthChecks.put("activity-service", getHealth("http://activity-service"));


            /* 추후에 각 서비스들 구현시 추가
            healthChecks.put("review-service", getHealth("http://review-service"));
            healthChecks.put("comment-service", getHealth("http://comment-service"));
            healthChecks.put("issue-service", getHealth("http://issue-service"));
             */

			// 모든 서비스의 상태 확인을 수행하는 Mono 리스트 생성
			List<Mono<Map.Entry<String, Health>>> monos = new ArrayList<>();

			for (Map.Entry<String, Mono<Health>> entry : healthChecks.entrySet()) {
				String serviceId = entry.getKey();
				Mono<Health> healthMono = entry.getValue();

				monos.add(healthMono.map(health -> Map.entry(serviceId, health)));
			}

			// 모든 상태 병합
			return Flux.merge(monos)
				.collectMap(Map.Entry::getKey, Map.Entry::getValue)
				.map(healthMap -> {
					Set<Status> statuses = new HashSet<>();
					for (Health health : healthMap.values()) {
						statuses.add(health.getStatus());
					}

					Status aggregateStatus = statusAggregator.getAggregateStatus(statuses);
					return Health.status(aggregateStatus)
						.withDetails(healthMap)
						.build();
				});
		};
	}

	private Mono<Health> getHealth(String url) {
		url += "/actuator/health";
		LOG.debug("Checking Health API: {}", url);
		return getWebClient().get().uri(url).retrieve().bodyToMono(String.class)
			.map(body -> new Health.Builder().up().build())
			.onErrorResume(ex -> Mono.just(new Health.Builder().down(ex).build()))
			.log();
	}

	private WebClient getWebClient() {
		if (webClient == null) {
			webClient = webClientBuilder.build();
		}
		return webClient;
	}
}
