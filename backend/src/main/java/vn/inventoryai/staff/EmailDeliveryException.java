package vn.inventoryai.staff;

class EmailDeliveryException extends RuntimeException {
    EmailDeliveryException(Throwable cause) {
        super("Invitation email delivery failed", cause);
    }
}
