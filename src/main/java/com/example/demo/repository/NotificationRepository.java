package com.example.demo.repository;

import com.example.demo.domain.Notification;
import com.example.demo.domain.NotificationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class NotificationRepository {

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Notification> mapper = (rs, n) -> {
        Notification x = new Notification();
        x.setId(rs.getLong("id"));
        x.setType(NotificationType.fromValue(rs.getString("type")));
        x.setRecipient(rs.getString("recipient"));
        x.setSubject(rs.getString("subject"));
        x.setContent(rs.getString("content"));
        Timestamp c = rs.getTimestamp("created_at");
        Timestamp u = rs.getTimestamp("updated_at");
        x.setCreatedAt(c == null ? null : c.toInstant());
        x.setUpdatedAt(u == null ? null : u.toInstant());
        return x;
    };

    public Notification insert(Notification n) {
        KeyHolder kh = new GeneratedKeyHolder();
        Instant now = Instant.now();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO notification(type, recipient, subject, content, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, n.getType().getValue());
            ps.setString(2, n.getRecipient());
            ps.setString(3, n.getSubject());
            ps.setString(4, n.getContent());
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(now));
            return ps;
        }, kh);
        n.setId(kh.getKey().longValue());
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        return n;
    }

    public Optional<Notification> findById(Long id) {
        return jdbc.query("SELECT * FROM notification WHERE id = ?", mapper, id).stream().findFirst();
    }

    public List<Notification> findRecent(int limit) {
        return jdbc.query("SELECT * FROM notification ORDER BY created_at DESC, id DESC LIMIT ?", mapper, limit);
    }

    public boolean existsById(Long id) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id = ?", Integer.class, id);
        return c != null && c > 0;
    }

    public int updateSubjectContent(Long id, String subject, String content) {
        return jdbc.update(
                "UPDATE notification SET subject = ?, content = ?, updated_at = ? WHERE id = ?",
                subject, content, Timestamp.from(Instant.now()), id);
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM notification WHERE id = ?", id);
    }
}
