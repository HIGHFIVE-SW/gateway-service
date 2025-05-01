package com.trendist.gateway_service.global.exception;

public class NotExistsAuthorization extends RuntimeException {
	public NotExistsAuthorization() {
		super("Authorization header does not exist");
	}
}
