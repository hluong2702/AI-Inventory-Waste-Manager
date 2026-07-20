package vn.inventoryai.staff;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InviteTokenRepository extends JpaRepository<InviteToken, Long> {
    @Query("select token from InviteToken token " +
            "join fetch token.user user " +
            "join fetch token.membership membership " +
            "join fetch membership.store " +
            "where token.tokenHash = :tokenHash")
    Optional<InviteToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from InviteToken token " +
            "join fetch token.user user " +
            "join fetch token.membership membership " +
            "join fetch membership.store " +
            "where token.tokenHash = :tokenHash")
    Optional<InviteToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    void deleteByUserId(Long userId);

    void deleteByMembershipId(Long membershipId);
}
