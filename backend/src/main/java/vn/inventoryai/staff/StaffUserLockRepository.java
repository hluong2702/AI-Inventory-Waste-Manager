package vn.inventoryai.staff;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.inventoryai.auth.AppUser;

import java.util.Optional;

interface StaffUserLockRepository extends Repository<AppUser, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user " +
            "left join fetch user.store where user.id = :userId")
    Optional<AppUser> findByIdForUpdate(@Param("userId") Long userId);
}
