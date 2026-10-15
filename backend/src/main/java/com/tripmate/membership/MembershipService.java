package com.tripmate.membership;

import com.tripmate.identity.User;
import com.tripmate.shared.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MembershipService {
    private final JdbcTemplate jdbc;
    public MembershipService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void requireMember(UUID trip, UUID user) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM trip_member WHERE trip_id=? AND user_id=?)", Boolean.class, trip, user);
        if (!Boolean.TRUE.equals(exists)) throw ApiException.notFound();
    }
    public List<User> members(UUID trip, UUID user) {
        requireMember(trip, user);
        return jdbc.query("SELECT u.id, u.display_name FROM app_user u JOIN trip_member m ON m.user_id=u.id WHERE m.trip_id=? ORDER BY m.joined_at, u.id",
            (rs,row) -> new User(rs.getObject("id", UUID.class), rs.getString("display_name")), trip);
    }
}
