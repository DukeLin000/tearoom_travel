package com.tripmate.budget;

import com.tripmate.identity.User;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BudgetController {
    private final BudgetService budgets;
    public BudgetController(BudgetService budgets) { this.budgets=budgets; }
    @GetMapping("/api/v1/trips/{tripId}/budget")
    public BudgetService.Budget get(@PathVariable UUID tripId,@AuthenticationPrincipal User user) { return budgets.get(tripId,user.id()); }
}
