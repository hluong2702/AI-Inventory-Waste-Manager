package vn.inventoryai.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.UserStatus;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndStoreId(String email, Long storeId);

    long countByStoreIdAndRoleAndStatusNot(Long storeId, Role role, UserStatus status);

    long countByStoreIdAndRoleInAndStatusNot(Long storeId, List<Role> roles, UserStatus status);

    long countByStatus(UserStatus status);

    List<AppUser> findByStoreIdAndRoleIn(Long storeId, List<Role> roles);
}
