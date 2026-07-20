package vn.inventoryai.daily;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {

    /**
     * Tìm cấu hình của tenant. Trả về Optional.empty() nếu chưa có
     * (trong trường hợp đó caller sẽ dùng settings mặc định).
     */
    Optional<TenantSettings> findByTenantId(Long tenantId);
}
