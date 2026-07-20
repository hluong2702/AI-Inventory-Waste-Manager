package vn.inventoryai.subscription;

public enum PaymentStatus {
    CREATING,
    CREATION_RECONCILING,
    PENDING,
    RECONCILING,
    SUCCESS,
    FAILED,
    CANCELLED,
    EXPIRED,
    REVIEW_REQUIRED,
    REFUNDED
}
