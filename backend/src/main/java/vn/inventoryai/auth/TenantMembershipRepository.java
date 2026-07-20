package vn.inventoryai.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.UserStatus;

import java.util.List;
import java.util.Optional;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, Long> {
    @EntityGraph(attributePaths = "store")
    Optional<TenantMembership> findByUserIdAndStoreIdAndStatusAndStoreStatus(
            Long userId,
            Long storeId,
            UserStatus membershipStatus,
            StoreStatus storeStatus
    );

    @EntityGraph(attributePaths = "store")
    Optional<TenantMembership> findFirstByUserIdAndStatusAndStoreStatusOrderByIdAsc(
            Long userId,
            UserStatus membershipStatus,
            StoreStatus storeStatus
    );

    @EntityGraph(attributePaths = "store")
    List<TenantMembership> findAllByUserIdAndStatusAndStoreStatusOrderByIdAsc(
            Long userId,
            UserStatus membershipStatus,
            StoreStatus storeStatus
    );

    Optional<TenantMembership> findByUserIdAndStoreId(Long userId, Long storeId);

    @EntityGraph(attributePaths = {"store", "user"})
    List<TenantMembership> findAllByStoreIdAndRoleInOrderByUserFullNameAsc(
            Long storeId,
            List<Role> roles
    );

    @EntityGraph(attributePaths = {"store", "user"})
    List<TenantMembership> findAllByUserIdInOrderByStoreNameAsc(List<Long> userIds);

    long countByStoreIdAndRoleInAndStatusNot(
            Long storeId,
            List<Role> roles,
            UserStatus excludedStatus
    );

    long countByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select membership
            from TenantMembership membership
            join fetch membership.user
            join fetch membership.store
            where membership.user.id = :userId
              and membership.store.id = :storeId
            """)
    Optional<TenantMembership> findByUserIdAndStoreIdForUpdate(
            @Param("userId") Long userId,
            @Param("storeId") Long storeId
    );

    boolean existsByUserIdAndStoreIdAndStatusAndStoreStatus(
            Long userId,
            Long storeId,
            UserStatus membershipStatus,
            StoreStatus storeStatus
    );
}
