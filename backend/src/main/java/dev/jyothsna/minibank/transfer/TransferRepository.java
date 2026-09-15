package dev.jyothsna.minibank.transfer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

	@Query("""
			select t from Transfer t
			join fetch t.fromAccount
			join fetch t.toAccount toAccount
			join fetch toAccount.owner
			where t.initiatedBy = :initiatedBy and t.idempotencyKey = :idempotencyKey
			""")
	Optional<Transfer> findByIdempotencyKey(UUID initiatedBy, String idempotencyKey);

	long countByCreatedAtAfter(Instant since);

	interface RecentPayee {

		String getAccountNumber();

		String getFullName();

	}

	/** Other people's accounts this user has paid, most recently paid first. */
	@Query("""
			select a.accountNumber as accountNumber, o.fullName as fullName
			from Transfer t join t.toAccount a join a.owner o
			where t.initiatedBy = :userId and o.id <> :userId
			group by a.accountNumber, o.fullName
			order by max(t.createdAt) desc
			""")
	List<RecentPayee> findRecentPayees(UUID userId, Pageable pageable);

}
