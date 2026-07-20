package vn.inventoryai.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.inventoryai.auth.TenantMembershipRepository;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;

import java.util.Objects;

@Service("storeAccess")
@RequiredArgsConstructor
public class StoreAccessService {
    private final TenantMembershipRepository membershipRepository;

    public boolean canAccessStore(Long storeId) {
        if (storeId == null) return false;
        UserPrincipal principal = SecurityUtils.principal();
        if (principal.role() == Role.SYSTEM_ADMIN || !Objects.equals(SecurityUtils.storeId(), storeId)) {
            return false;
        }
        return membershipRepository.existsByUserIdAndStoreIdAndStatusAndStoreStatus(
                principal.userId(), storeId, UserStatus.ACTIVE, StoreStatus.ACTIVE
        );
    }

    public void assertCurrentStore(Long storeId) {
        if (!canAccessStore(storeId)) {
            throw new AppException(
                    ErrorCode.STORE_MISMATCH,
                    HttpStatus.FORBIDDEN,
                    "Store is not the authenticated active tenant"
            );
        }
    }
}
