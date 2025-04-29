package com.trendist.gateway_service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendist.gateway_service.exception.AccessTokenExpiredException;
import com.trendist.gateway_service.exception.NotExistsAuthorization;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class JwtAuthorizationFilter implements GatewayFilter {

    private static final String AUTH_TYPE = "Bearer ";
    private static final String X_GATEWAY_HEADER = "X-Gateway-User-Id";

    @Value("${auth.jwt.key}")
    private String key;

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain){
        log.info("JwtAuthorization begin");
        try {
            // Authorization 헤더 추출
            List<String> authorizations = getAuthorizations(exchange);

            // 헤더가 없거나 빈 경우 예외
            if (isNotExistsAuthorizationHeader(authorizations)) {
                throw new NotExistsAuthorization();
            }

            // Bearer 타입인 토큰만 필터링
            String authorization = authorizations.stream()
                    .filter(this::isBearerType)
                    .findFirst()
                    .orElseThrow(NotExistsAuthorization::new);

            // 실제 토큰만 추출
            String jwtToken = parseAuthorizationToken(authorization);

            // 만료 여부 확인
            if (isValidExpire(jwtToken)) {
                throw new AccessTokenExpiredException();
            }

            // 토큰에서 subject 추출해서 헤더에 추가
            exchange.getRequest().mutate().header(X_GATEWAY_HEADER, getSubjectOf(jwtToken));

            return chain.filter(exchange);
        } catch(NotExistsAuthorization e1) {
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

    // Authorization 헤더가 Bearer 타입인지 확인
    private boolean isBearerType(String authorization){
        return authorization.startsWith(AUTH_TYPE);
    }


    private List<String> getAuthorizations(ServerWebExchange exchange){
        ServerHttpRequest request = exchange.getRequest();
        return request.getHeaders().get(HttpHeaders.AUTHORIZATION);
    }


    // Bearer 접두사 제거하고 토큰만 추출
    private String parseAuthorizationToken(String authorization){
        return authorization.replace(AUTH_TYPE, "").trim();
    }

    // Authorization 헤더 유무 체크
    private boolean isNotExistsAuthorizationHeader(List<String> authorizations){
        return authorizations == null || authorizations.isEmpty();
    }

    // 토큰에서 subject 추출
    private String getSubjectOf(String jwtToken){
        return Jwts.parser().verifyWith(secretKey())
                .build()
                .parseSignedClaims(jwtToken)
                .getPayload()
                .getSubject();
    }

    /**
     * 토큰 만료 시간 검사
     */
    private boolean isValidExpire(String jwtToken){
        Date expiration = Jwts.parser().verifyWith(secretKey())
                .build()
                .parseSignedClaims(jwtToken)
                .getPayload()
                .getExpiration();
        return expiration.before(new Date());
    }


    // JWT 서명 검증용 SecretKey 생성
    private SecretKey secretKey(){
        return Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
    }

    record ErrorResponse(int errorCode, String message){}
}
