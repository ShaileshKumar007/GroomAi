package com.groomai.auth.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

public class TokenExpiredException extends GroomAiException {
    public TokenExpiredException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED");
    }
}
