package com.tripmate.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AuditService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(UUID trip, UUID actor, String action, UUID entity, Object before, Object after) {
        try {
            jdbc.update("INSERT INTO trip_change_log(trip_id,actor_id,action,entity_id,before_data,after_data) VALUES (?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb))",
                trip,actor,action,entity,before == null ? null : mapper.writeValueAsString(before),after == null ? null : mapper.writeValueAsString(after));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize audit data",exception); }
    }
}
