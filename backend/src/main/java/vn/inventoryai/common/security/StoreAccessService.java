package vn.inventoryai.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.store.StoreRepository;

@Service("storeAccess")
public class StoreAccessService {
    private final StoreRepository storeRepository;

    public StoreAccessService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public boolean canAccessStore(Long storeId) {
        UserPrincipal principal = SecurityUtils.principal();
        if (principal.role() == Role.SYSTEM_ADMIN || (principal.storeId() != null && principal.storeId().equals(storeId))) {
            return true;
        }
        return storeRepository.findById(storeId)
                .map(store -> store.getOwner() != null && store.getOwner().getId().equals(principal.userId()))
                .orElse(false);
    }

    public void assertCurrentStore(Long storeId) {
        if (!canAccessStore(storeId)) {
            throw new AppException(ErrorCode.STORE_MISMATCH, HttpStatus.FORBIDDEN, "storeId does not match JWT storeId");
        }
    }
}
