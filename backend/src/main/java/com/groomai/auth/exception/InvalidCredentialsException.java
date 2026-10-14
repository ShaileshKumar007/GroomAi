package com.groomai.auth.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends GroomAiException {
    public InvalidCredentialsException() {
        super("Invalid email or password.", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }
}
