package com.my.brain.domain.exception;

/**
 * 왜: 구글 자격 증명이 없을 때 사용자에게 인증 링크를 안내하기 위해 링크 정보를 담아 전달하기 위함.
 */
public class GoogleCredentialNotFoundException extends RuntimeException {

    private final String authUrl;

    public GoogleCredentialNotFoundException(String authUrl) {
        super("Google credential not found");
        this.authUrl = authUrl;
    }

    public String authUrl() {
        return authUrl;
    }
}
