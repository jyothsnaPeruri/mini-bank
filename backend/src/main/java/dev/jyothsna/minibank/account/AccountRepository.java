package dev.jyothsna.minibank.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	List<Account> findByOwnerIdOrderByCreatedAt(UUID ownerId);

	Optional<Account> findByAccountNumber(String accountNumber);

	boolean existsByAccountNumber(String accountNumber);

	long countByOwnerId(UUID ownerId);

	/** Looks up only the id, so no account state is cached before the row is locked. */
	@Query("select a.id from Account a where a.accountNumber = :accountNumber")
	Optional<UUID> findIdByAccountNumber(String accountNumber);

	/** SELECT ... FOR UPDATE: other transactions touching this account wait until we commit. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a join fetch a.owner where a.id = :id")
	Optional<Account> findByIdForUpdate(UUID id);

	@Query("select a from Account a join fetch a.owner order by a.createdAt")
	List<Account> findAllWithOwner();

	@Query("select coalesce(sum(a.balance), 0) from Account a")
	BigDecimal totalBalance();

}
