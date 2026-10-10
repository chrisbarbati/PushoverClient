package dev.chrisbarbati.pushoverclient.exception;

public class InvalidEncryptionKeyException extends PushoverClientException{

    public InvalidEncryptionKeyException(String message) {
        super(message);
    }

    public  InvalidEncryptionKeyException(String message, Throwable cause) {
        super(message, cause);
    }

}