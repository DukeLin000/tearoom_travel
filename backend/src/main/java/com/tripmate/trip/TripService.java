package com.tripmate.trip;

import com.tripmate.membership.MembershipService;
import com.tripmate.shared.ApiException;
import com.tripmate.shared.Money;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {
    public record CreateTrip(@NotBlank @Size(max=120) String title, @NotBlank @Size(max=120) String destination,
        @NotNull LocalDate startDate, @NotNull LocalDate endDate, @NotBlank @Size(max=64) String timezone,
        @NotBlank @Pattern(regexp="[A-Z]{3}") String currency, @NotNull String budgetLimit) {}
    public record Trip(UUID id, String title, String destination, LocalDate startDate, LocalDate endDate,
        String timezone, String currency, String budgetLimit, UUID ownerUserId, long version, int memberCount) {}
    public record TripPage(List<Trip> items, int page, int size, long total) {}
    private final JdbcTemplate jdbc;
    private final MembershipService memberships;
    private static final String SELECT = """
        SELECT t.*, (SELECT COUNT(*) FROM trip_member m WHERE m.trip_id=t.id) AS member_count FROM trip t
        """;
    private static final RowMapper<Trip> MAPPER = (rs,row) -> new Trip(rs.getObject("id",UUID.class), rs.getString("title"),
        rs.getString("destination"), rs.getObject("start_date",LocalDate.class), rs.getObject("end_date",LocalDate.class),
        rs.getString("timezone"), rs.getString("currency").trim(), Money.format(rs.getBigDecimal("budget_limit")),
        rs.getObject("owner_user_id",UUID.class), rs.getLong("version"), rs.getInt("member_count"));
    public TripService(JdbcTemplate jdbc, MembershipService memberships) { this.jdbc=jdbc; this.memberships=memberships; }
    @Transactional
    public Trip create(CreateTrip input, UUID user) {
        if (input.endDate().isBefore(input.startDate()) || input.endDate().toEpochDay()-input.startDate().toEpochDay() >= 14) {
            throw ApiException.invalid("Trip dates must span 1 to 14 days");
        }
        if (!ZoneId.getAvailableZoneIds().contains(input.timezone())) throw ApiException.invalid("Use a valid IANA timezone");
        try { Currency.getInstance(input.currency()); } catch (IllegalArgumentException exception) { throw ApiException.invalid("Invalid ISO 4217 currency"); }
        var budget = Money.parse(input.budgetLimit());
        UUID trip = UUID.randomUUID();
        jdbc.update("INSERT INTO trip(id,title,destination,start_date,end_date,timezone,currency,budget_limit,owner_user_id) VALUES (?,?,?,?,?,?,?,?,?)",
            trip,input.title().trim(),input.destination().trim(),input.startDate(),input.endDate(),input.timezone(),input.currency(),budget,user);
        jdbc.update("INSERT INTO trip_member(trip_id,user_id) VALUES (?,?)",trip,user);
        for (LocalDate date=input.startDate(); !date.isAfter(input.endDate()); date=date.plusDays(1)) {
            jdbc.update("INSERT INTO trip_day(id,trip_id,local_date) VALUES (?,?,?)",UUID.randomUUID(),trip,date);
        }
        return get(trip,user);
    }
    @Transactional(readOnly=true)
    public Trip get(UUID trip, UUID user) {
        memberships.requireMember(trip,user);
        return jdbc.query(SELECT+" WHERE t.id=?",MAPPER,trip).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    @Transactional(readOnly=true)
    public TripPage list(UUID user, int page, int size) {
        List<Trip> trips = jdbc.query(SELECT+" JOIN trip_member mine ON mine.trip_id=t.id AND mine.user_id=? ORDER BY t.created_at DESC,t.id LIMIT ? OFFSET ?",
            MAPPER,user,size,(long)page*size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM trip_member WHERE user_id=?",Long.class,user);
        return new TripPage(trips,page,size,total == null ? 0 : total);
    }
}
