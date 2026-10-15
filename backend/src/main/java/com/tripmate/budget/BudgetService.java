package com.tripmate.budget;

import com.tripmate.shared.Money;
import com.tripmate.trip.TripService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {
    public record Budget(String budgetLimit,String plannedTotal,String remaining,String estimatedPerPerson,
        int memberCount, Map<String,String> breakdown) {}
    private final JdbcTemplate jdbc;
    private final TripService trips;
    public BudgetService(JdbcTemplate jdbc,TripService trips) { this.jdbc=jdbc; this.trips=trips; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Budget get(UUID trip,UUID user) {
        var details=trips.get(trip,user);
        Map<String,String> breakdown=new LinkedHashMap<>();
        jdbc.query("SELECT i.category,SUM(i.estimated_amount) AS amount FROM itinerary_item i JOIN trip_day d ON d.id=i.day_id WHERE d.trip_id=? AND i.deleted_at IS NULL GROUP BY i.category ORDER BY i.category",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> breakdown.put(rs.getString("category"),Money.format(rs.getBigDecimal("amount"))),trip);
        BigDecimal total=breakdown.values().stream().map(BigDecimal::new).reduce(new BigDecimal("0.00"),BigDecimal::add);
        return new Budget(details.budgetLimit(),Money.format(total),Money.format(new BigDecimal(details.budgetLimit()).subtract(total)),
            total.divide(BigDecimal.valueOf(details.memberCount()),2,RoundingMode.HALF_UP).toPlainString(),details.memberCount(),breakdown);
    }
}
