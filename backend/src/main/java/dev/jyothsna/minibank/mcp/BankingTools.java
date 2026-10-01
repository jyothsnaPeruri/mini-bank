package dev.jyothsna.minibank.mcp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.jyothsna.minibank.account.AccountResponse;
import dev.jyothsna.minibank.account.AccountService;
import dev.jyothsna.minibank.account.AccountService.HistoryFilter;
import dev.jyothsna.minibank.account.AccountStatus;
import dev.jyothsna.minibank.account.LedgerEntryResponse;
import dev.jyothsna.minibank.account.PayeeResponse;
import dev.jyothsna.minibank.common.ApiException;
import dev.jyothsna.minibank.transfer.TransferRequest;
import dev.jyothsna.minibank.transfer.TransferResponse;
import dev.jyothsna.minibank.transfer.TransferService;
import io.modelcontextprotocol.common.McpTransportContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The banking tools an AI assistant can call over MCP.
 *
 * <p>
 * Every tool acts as the customer whose token the request carried, and delegates to the same services the REST API
 * uses — so ownership checks, the $10,000 limit, overdraft protection, row locking and idempotency are the existing,
 * tested rules rather than a parallel copy. The read-only tools are marked {@code readOnlyHint}; the one tool that
 * moves money is marked {@code destructiveHint} and only works with a code from {@code prepare_transfer}.
 */
@Component
class BankingTools {

	private static final int MAX_TRANSACTIONS = 50;

	private final AccountService accounts;

	private final TransferService transfers;

	private final PendingTransfers pending;

	private final Validator validator;

	BankingTools(AccountService accounts, TransferService transfers, PendingTransfers pending, Validator validator) {
		this.accounts = accounts;
		this.transfers = transfers;
		this.pending = pending;
		this.validator = validator;
	}

	/* ------------------------------------------------------------ reading -- */

	@McpTool(name = "list_accounts", title = "List my accounts",
			description = "Lists the signed-in customer's bank accounts with their ids, numbers, nicknames, types and current balances. Call this first: the other tools need an account id from here.",
			annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = false))
	public List<AccountResponse> listAccounts(McpTransportContext context) {
		return accounts.listForOwner(customer(context));
	}

	@McpTool(name = "get_account", title = "Get one account",
			description = "Returns one of the customer's accounts, including its current balance.",
			annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = false))
	public AccountResponse getAccount(McpTransportContext context,
			@McpToolParam(description = "Account id from list_accounts") String accountId) {
		return accounts.getForOwner(customer(context), parseId(accountId));
	}

	@McpTool(name = "recent_transactions", title = "Recent transactions",
			description = "Lists an account's most recent transactions, newest first. Negative amounts are money out.",
			annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = false))
	public List<LedgerEntryResponse> recentTransactions(McpTransportContext context,
			@McpToolParam(description = "Account id from list_accounts") String accountId,
			@McpToolParam(required = false, description = "How many to return, 1-50. Defaults to 10.") Integer limit,
			@McpToolParam(required = false, description = "\"in\" for money received, \"out\" for money spent, omit for both") String direction,
			@McpToolParam(required = false, description = "Only transactions whose description or counterparty contains this text") String search) {
		int size = limit == null ? 10 : Math.clamp(limit, 1, MAX_TRANSACTIONS);
		return accounts
			.history(customer(context), parseId(accountId), new HistoryFilter(direction, null, null, search), 0, size)
			.content();
	}

	@McpTool(name = "find_payee", title = "Look up a payee",
			description = "Looks up who owns a 10-digit account number before paying them. The name is partly masked (\"Alex C.\") for privacy.",
			annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = false))
	public PayeeResponse findPayee(McpTransportContext context,
			@McpToolParam(description = "The payee's 10-digit account number") String accountNumber) {
		customer(context); // signed-in customers only
		return accounts.lookupPayee(accountNumber.strip());
	}

	/* ------------------------------------------------------- moving money -- */

	record PreparedTransfer(String confirmationCode, String fromAccount, String toAccountNumber, String payee,
			BigDecimal amount, String description, BigDecimal balanceAfter, Instant expiresAt, String nextStep) {
	}

	@McpTool(name = "prepare_transfer", title = "Prepare a transfer (no money moves)",
			description = "Checks a transfer and returns a preview with a confirmation code. Does NOT move any money. Show the preview to the customer and ask them to confirm; only then call confirm_transfer with the code. Codes expire after 5 minutes.",
			annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false,
					openWorldHint = false))
	public PreparedTransfer prepareTransfer(McpTransportContext context,
			@McpToolParam(description = "Account id to pay from, from list_accounts") String fromAccountId,
			@McpToolParam(description = "The payee's 10-digit account number") String toAccountNumber,
			@McpToolParam(description = "Amount in dollars, e.g. 50 or 12.50. Maximum 10000.") BigDecimal amount,
			@McpToolParam(required = false, description = "A short note for the payment, up to 140 characters") String description) {
		UUID customerId = customer(context);
		TransferRequest request = new TransferRequest(parseId(fromAccountId),
				toAccountNumber == null ? null : toAccountNumber.strip(), amount, description);
		validate(request);

		// The same checks the transfer itself will make, run now so the preview never promises something that
		// confirm_transfer would refuse.
		AccountResponse from = accounts.getForOwner(customerId, request.fromAccountId());
		if (from.accountNumber().equals(request.toAccountNumber())) {
			throw ApiException.badRequest("same_account", "You can't transfer money to the same account");
		}
		if (from.status() != AccountStatus.ACTIVE) {
			throw ApiException.conflict("account_frozen", "Your account is frozen. Please contact support.");
		}
		if (from.balance().compareTo(amount) < 0) {
			throw ApiException.unprocessable("insufficient_funds", "Insufficient funds in " + from.nickname());
		}
		PayeeResponse payee = accounts.lookupPayee(request.toAccountNumber());

		PendingTransfers.Pending held;
		try {
			held = pending.hold(customerId, request, payee.holderName());
		}
		catch (IllegalStateException ex) {
			throw ApiException.conflict("too_many_pending", ex.getMessage());
		}
		return new PreparedTransfer(held.code(), from.nickname() + " (" + from.accountNumber() + ")",
				request.toAccountNumber(), payee.holderName(), amount.setScale(2), request.description(),
				from.balance().subtract(amount).setScale(2), held.expiresAt(),
				"Show these details to the customer and ask them to confirm. Call confirm_transfer only after they explicitly agree.");
	}

	record ConfirmedTransfer(TransferResponse transfer, boolean alreadyConfirmed, String message) {
	}

	@McpTool(name = "confirm_transfer", title = "Confirm a prepared transfer (moves money)",
			description = "Sends a transfer that prepare_transfer previewed, using its confirmation code. This moves real money: only call it after the customer has explicitly confirmed the preview. Confirming the same code twice does not pay twice.",
			annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
					openWorldHint = false))
	public ConfirmedTransfer confirmTransfer(McpTransportContext context,
			@McpToolParam(description = "The confirmation code returned by prepare_transfer, e.g. MB-7K3QX9PA") String confirmationCode) {
		UUID customerId = customer(context);
		PendingTransfers.Pending held = pending.find(customerId, confirmationCode)
			.orElseThrow(() -> ApiException.notFound(
					"That confirmation code is invalid or has expired. Prepare the transfer again."));

		// The code doubles as the idempotency key, so a retried or repeated confirm replays the original transfer
		// through the existing duplicate-protection instead of sending the money again.
		TransferService.Outcome outcome = transfers.transfer(customerId, held.request(), "mcp:" + held.code());
		String message = outcome.replayed() ? "This transfer was already sent; no money was moved again."
				: "Sent $" + outcome.transfer().amount() + " to " + withFullStop(held.payeeName());
		return new ConfirmedTransfer(outcome.transfer(), outcome.replayed(), message);
	}

	/* ------------------------------------------------------------ helpers -- */

	private static UUID customer(McpTransportContext context) {
		Object id = context == null ? null : context.get(McpServerConfig.USER_ID);
		if (id instanceof UUID uuid) {
			return uuid;
		}
		throw ApiException.unauthorized("Sign in first: send your Mini Bank token as a Bearer token.");
	}

	/** Masked names already end in a full stop ("Alex C."); don't add a second. */
	private static String withFullStop(String text) {
		return text.endsWith(".") ? text : text + ".";
	}

	private static UUID parseId(String value) {
		try {
			return UUID.fromString(value.strip());
		}
		catch (RuntimeException ex) {
			throw ApiException.badRequest("invalid_id", "That doesn't look like an account id. Use list_accounts first.");
		}
	}

	private void validate(TransferRequest request) {
		for (ConstraintViolation<TransferRequest> violation : validator.validate(request)) {
			throw ApiException.badRequest("invalid_transfer", violation.getMessage());
		}
	}

}
