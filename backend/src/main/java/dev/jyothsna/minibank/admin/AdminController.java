package dev.jyothsna.minibank.admin;

import java.util.List;
import java.util.UUID;

import dev.jyothsna.minibank.account.AccountResponse;
import dev.jyothsna.minibank.account.AccountStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only reachable with the ADMIN role - see SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
class AdminController {

	private final AdminService adminService;

	AdminController(AdminService adminService) {
		this.adminService = adminService;
	}

	record UpdateStatusRequest(@NotNull(message = "Choose a status") AccountStatus status) {
	}

	@GetMapping("/stats")
	AdminService.Stats stats() {
		return adminService.stats();
	}

	@GetMapping("/customers")
	List<AdminService.CustomerResponse> customers() {
		return adminService.customers();
	}

	@PatchMapping("/accounts/{id}")
	AccountResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
		return adminService.setStatus(id, request.status());
	}

}
