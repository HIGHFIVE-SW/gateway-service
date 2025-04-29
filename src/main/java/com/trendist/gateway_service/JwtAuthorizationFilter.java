package  com.trendist.gateway_service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@Component
public class JwtAuthorizationFilter implements GatewayFilter {

    @Value("${auth.jwt.key}")
    private String key;

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain){
        log.info("JwtAuthorization begin");
        try {
            List<String> authorizations = getAuthorizations(exchange);

            if (isNotExistsAuthorizationHeader(authorizations)) {
                throw new NotExistsAuthorization();
            }

            String authorization = authorizations.stream()
                    .filter(this::isBearerType)
                    .findFirst()
                    .orElseThrow(NotExistsAuthorization::new);

            String jwtToken = parseAuthorizationToken(authorization);
            if (isValidExpire(jwtToken)) {
                throw new AccessTokenExpiredException();
            }

            exchange.getRequest().mutate().header(X_GATEWAY_HEADER, getSubjectOf(jwtToken));
            return chain.filter(exchange);
        } catch(NotExistAuthorization e1) {
            return sendErrorResponse(exchange, 701, e1);
        } catch(AccessTokenExpiredException e2) {
            return sendErrorResponse(exchange, 702, e2);
        } catch(Exception e3){
            return sendErrorResponse(exchange, 999, e3);
        }
    }

    private Mono<Void> sendErrorResponse(ServerWebExchange exchange, int errorCode, Exception e){
        try{
            ErrorResponse errorResponse = new ErrorResponse(errorCode, e.getMessage());
            String errorBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorResponse);
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch(JsonProcessingException ex){
            throw new RuntimeException(ex);
        }
    }

    private boolean isBearerType(String authorization){
        return authorization.startsWith(AUTH_TYPE);
    }

    private List<String> getAuthorizations(ServerWebExchange exchange){
        ServerHttpRequset request = exchange.getRequest();
        return request.getHeaders().get(HttpHeaders.AUTHORIZATION);
    }

    private String parseAuthorizationToken(String authorization){
        return authorization.replace(AUTH_TYPE, "").trim();
    }

    private boolean isNotExistsAuthorizationHeader(List<String> authorizations){
        return authorizations == null || authorizations.isEmpty();
    }

    private String getSubjectOf(String jwtToken){
        return Jwts.parser().verifyWith(secretKey())
                .build()
                .parseSignedClaims(jwtToken)
                .getPayload()
                .getSubject();
    }

    private boolean isValidateExpire(String jwtToken){
        Date expiration = Jwts.parser().verifyWith(secretKey())
                .build()
                .parseSignedClaims(jwtToken)
                .getPayload()
                .getExpiration();
        return expiration.before(new Date());
    }

    private SecretKey secretKey(){
        return Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
    }

    record ErrorResponse(int errorCode, String message){}
}

@RequiredArgsConstructor
@Configuration
public class GatewayConfiguration{
    private final JwtAuthorizationFilter jwtAuthorizationFilter;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder){
        return builder.routes()
                .route("user-service", predicateSpec -> predicateSpec
                        .path("sign-up", "/login")
                        .and().method(HttpMethod.POST)
                        .filters(gatewayFilterSpec -> gatewayFilterSpec
                                .removeRequestHeader(HttpHeaders.COOKIE)
                        )
                        .uri("lb://USER-SERVICE")
                )
                .route("post-service", predicateSpec -> predicateSpec
                        .path("")
                        .filters(gatewayFilterSpec -> gatewayFilterSpec
                                .removeRequestHeader(HTttpHeaders.COOKIE)
                                .filter(jwtAuthorizationFilter)
                        )
                        .uri("lb://POST-SERVICE")
                )
                .build();
    }
}