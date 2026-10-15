package com.tripmate.itinerary;

import com.tripmate.shared.Money;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

public record Item(UUID id, UUID dayId, String title, String category, LocalTime localStartTime, LocalTime localEndTime,
    int sortOrder, String locationName, String mapUrl, String estimatedAmount, String note,
    long version, Instant updatedAt, Instant deletedAt) {
    public static final RowMapper<Item> MAPPER = (rs,row) -> new Item(rs.getObject("id",UUID.class),rs.getObject("day_id",UUID.class),
        rs.getString("title"),rs.getString("category"),rs.getObject("local_start_time",LocalTime.class),rs.getObject("local_end_time",LocalTime.class),
        rs.getInt("sort_order"),rs.getString("location_name"),rs.getString("map_url"),Money.format(rs.getBigDecimal("estimated_amount")),
        rs.getString("note"),rs.getLong("version"),rs.getTimestamp("updated_at").toInstant(),
        rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant());
}
