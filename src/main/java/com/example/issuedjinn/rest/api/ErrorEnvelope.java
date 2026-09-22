package com.example.issuedjinn.rest.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error response envelope for REST API errors.
 */
public class ErrorEnvelope {
    
    private final ErrorDetails error;

    public ErrorEnvelope(ErrorDetails error) {
        this.error = error;
    }

    public ErrorDetails getError() {
        return error;
    }

    public static class ErrorDetails {
        private final String code;
        private final String message;

        @JsonCreator
        public ErrorDetails(
                @JsonProperty("code") String code,
                @JsonProperty("message") String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    public static ErrorEnvelope of(String code, String message) {
        return new ErrorEnvelope(new ErrorDetails(code, message));
    }
}
