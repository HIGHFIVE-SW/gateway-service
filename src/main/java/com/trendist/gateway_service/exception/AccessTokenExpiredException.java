// AccessTokenExpiredException.java
package com.trendist.gateway_service.exception;

public class AccessTokenExpiredException extends RuntimeException {
    public AccessTokenExpiredException() {
        super("Access token has expired");
    }
}