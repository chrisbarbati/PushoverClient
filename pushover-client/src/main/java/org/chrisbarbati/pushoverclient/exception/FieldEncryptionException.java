package org.chrisbarbati.pushoverclient.exception;

public class FieldEncryptionException extends PushoverClientException{

    public FieldEncryptionException(String message) {
        super(message);
    }

    public FieldEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }

}