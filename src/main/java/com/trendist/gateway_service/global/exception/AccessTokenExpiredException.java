package com.trendist.gateway_service.global.exception;

public class AccessTokenExpiredException extends RuntimeException {
	public AccessTokenExpiredException() {
		super("Access token has expired");
	}
}
