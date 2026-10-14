package com.groomai.auth.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends GroomAiException {
    public InvalidTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
}
