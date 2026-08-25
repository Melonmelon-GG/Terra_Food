package com.dayan.food.service;

public class RegistrationCodeDeliveryException extends RuntimeException {

    public RegistrationCodeDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public RegistrationCodeDeliveryException(String message) {
        super(message);
    }
}
