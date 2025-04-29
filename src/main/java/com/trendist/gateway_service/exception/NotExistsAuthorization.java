package com.trendist.gateway_service.exception;

public class NotExistsAuthorization extends RuntimeException {
    public NotExistsAuthorization() {
        super("Authorization header does not exist");
    }
}
