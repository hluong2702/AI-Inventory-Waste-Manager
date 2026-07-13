package vn.inventoryai.common.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static UserPrincipal principal() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public static Long storeId() {
        Long tenantStoreId = TenantContext.getStoreId();
        return tenantStoreId == null ? principal().storeId() : tenantStoreId;
    }
}
