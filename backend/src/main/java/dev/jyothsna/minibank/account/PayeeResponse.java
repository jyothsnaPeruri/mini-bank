package dev.jyothsna.minibank.account;

/** What a payer sees before sending money: enough to confirm the payee, without exposing their full name. */
public record PayeeResponse(String accountNumber, String holderName) {

	static PayeeResponse from(Account account) {
		return new PayeeResponse(account.getAccountNumber(), mask(account.getOwner().getFullName()));
	}

	/** "Alex Chen" becomes "Alex C." */
	public static String mask(String fullName) {
		String[] parts = fullName.strip().split("\\s+");
		if (parts.length == 1) {
			return parts[0];
		}
		return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
	}

}
