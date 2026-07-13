package vn.inventoryai.common.security;

public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT_STORE_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setStoreId(Long storeId) {
        CURRENT_STORE_ID.set(storeId);
    }

    public static Long getStoreId() {
        return CURRENT_STORE_ID.get();
    }

    public static void clear() {
        CURRENT_STORE_ID.remove();
    }
}
