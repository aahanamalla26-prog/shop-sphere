package com.shopsphere.core.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productName, int available, int requested) {
        super("Insufficient stock for '" + productName + "': requested " + requested + ", only " + available + " available");
    }
}
