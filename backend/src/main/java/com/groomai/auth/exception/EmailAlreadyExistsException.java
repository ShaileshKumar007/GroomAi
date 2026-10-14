package com.groomai.auth.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends GroomAiException {
    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' is already registered.", HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
    }
}
