package com.tripmate.itinerary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tripmate.audit.AuditService;
import com.tripmate.membership.MembershipService;
import com.tripmate.shared.ApiException;
import com.tripmate.shared.Money;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryService {
    public record Day(UUID id, LocalDate localDate, long version, List<Item> items) {}
    public record Schedule(UUID tripId, List<Day> days) {}
    private static final Set<String> FIELDS=Set.of("title","category","localStartTime","localEndTime","locationName","mapUrl","estimatedAmount","note");
    private final JdbcTemplate jdbc;
    private final MembershipService memberships;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Validator validator;
    public ItineraryService(JdbcTemplate jdbc, MembershipService memberships, AuditService audit, ObjectMapper mapper, Validator validator) {
        this.jdbc=jdbc; this.memberships=memberships; this.audit=audit; this.mapper=mapper; this.validator=validator;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Schedule schedule(UUID trip, UUID user) {
        memberships.requireMember(trip,user);
        var days=jdbc.query("SELECT id,local_date,version FROM trip_day WHERE trip_id=? ORDER BY local_date", (rs,row) ->
            new Day(rs.getObject("id",UUID.class),rs.getObject("local_date",LocalDate.class),rs.getLong("version"),List.of()), trip);
        return new Schedule(trip,days.stream().map(day -> new Day(day.id(),day.localDate(),day.version(),
            jdbc.query("SELECT * FROM itinerary_item WHERE day_id=? AND deleted_at IS NULL ORDER BY sort_order,id",Item.MAPPER,day.id()))).toList());
    }
    private void lockDay(UUID trip, UUID day) {
        if (jdbc.query("SELECT id FROM trip_day WHERE id=? AND trip_id=? FOR UPDATE",(rs,row)->rs.getObject(1,UUID.class),day,trip).isEmpty()) {
            throw ApiException.notFound();
        }
    }
    private Item liveItem(UUID trip, UUID item) {
        return jdbc.query("SELECT i.* FROM itinerary_item i JOIN trip_day d ON d.id=i.day_id WHERE i.id=? AND d.trip_id=? AND i.deleted_at IS NULL",
            Item.MAPPER,item,trip).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    private void validate(ItemFields fields) {
        if (!validator.validate(fields).isEmpty()) throw ApiException.invalid("Invalid item fields or lengths");
        fields.validateDomain();
    }
    @Transactional
    public Item create(UUID trip, UUID day, ItemFields fields, UUID user) {
        memberships.requireMember(trip,user); validate(fields); lockDay(trip,day);
        int position=jdbc.queryForObject("SELECT COUNT(*) FROM itinerary_item WHERE day_id=? AND deleted_at IS NULL",Integer.class,day);
        UUID item=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO itinerary_item(id,day_id,title,category,local_start_time,local_end_time,sort_order,location_name,map_url,estimated_amount,note,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,item,day,fields.title().trim(),fields.category(),fields.localStartTime(),fields.localEndTime(),position,fields.locationName(),fields.mapUrl(),Money.parse(fields.estimatedAmount()),fields.note(),user,user);
        jdbc.update("UPDATE trip_day SET version=version+1 WHERE id=?",day);
        Item created=liveItem(trip,item);
        audit.record(trip,user,"ITEM_CREATED",item,null,created);
        return created;
    }
    @Transactional
    public Item patch(UUID trip, UUID item, JsonNode patch, UUID user) {
        memberships.requireMember(trip,user);
        if (!patch.isObject() || !patch.has("expectedVersion") || !patch.get("expectedVersion").isIntegralNumber()
            || !patch.get("expectedVersion").canConvertToLong() || patch.get("expectedVersion").longValue()<0) {
            throw ApiException.invalid("expectedVersion must be a nonnegative integer");
        }
        patch.fieldNames().forEachRemaining(field -> { if (!field.equals("expectedVersion") && !FIELDS.contains(field)) throw ApiException.invalid("Unknown or immutable item field: "+field); });
        if (patch.size()<2) throw ApiException.invalid("Supply at least one editable field");
        lockDay(trip,liveItem(trip,item).dayId());
        Item before=liveItem(trip,item);
        if (before.version()!=patch.get("expectedVersion").longValue()) throw ApiException.conflict();
        ObjectNode merged=mapper.createObjectNode();
        JsonNode current=mapper.valueToTree(before);
        FIELDS.forEach(field -> merged.set(field,current.get(field)));
        patch.fields().forEachRemaining(entry -> { if (FIELDS.contains(entry.getKey())) merged.set(entry.getKey(),entry.getValue()); });
        ItemFields fields;
        try { fields=mapper.treeToValue(merged,ItemFields.class); } catch (JsonProcessingException exception) { throw ApiException.invalid("Invalid item field type or time format"); }
        validate(fields);
        int changed=jdbc.update("""
            UPDATE itinerary_item SET title=?,category=?,local_start_time=?,local_end_time=?,location_name=?,map_url=?,estimated_amount=?,note=?,version=version+1,updated_by=?,updated_at=now()
            WHERE id=? AND version=? AND deleted_at IS NULL
            """,fields.title().trim(),fields.category(),fields.localStartTime(),fields.localEndTime(),fields.locationName(),fields.mapUrl(),Money.parse(fields.estimatedAmount()),fields.note(),user,item,before.version());
        if (changed!=1) throw ApiException.conflict();
        Item after=liveItem(trip,item);
        audit.record(trip,user,"ITEM_UPDATED",item,before,after);
        return after;
    }
    @Transactional
    public void delete(UUID trip, UUID item, long expectedVersion, UUID user) {
        memberships.requireMember(trip,user);
        lockDay(trip,liveItem(trip,item).dayId());
        Item before=liveItem(trip,item);
        if (before.version()!=expectedVersion) throw ApiException.conflict();
        int changed=jdbc.update("UPDATE itinerary_item SET deleted_at=now(),updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND version=? AND deleted_at IS NULL",user,item,expectedVersion);
        if (changed!=1) throw ApiException.conflict();
        jdbc.update("UPDATE itinerary_item SET sort_order=sort_order-1 WHERE day_id=? AND sort_order>? AND deleted_at IS NULL",before.dayId(),before.sortOrder());
        jdbc.update("UPDATE trip_day SET version=version+1 WHERE id=?",before.dayId());
        Item after=jdbc.queryForObject("SELECT * FROM itinerary_item WHERE id=?",Item.MAPPER,item);
        audit.record(trip,user,"ITEM_DELETED",item,before,after);
    }
}
