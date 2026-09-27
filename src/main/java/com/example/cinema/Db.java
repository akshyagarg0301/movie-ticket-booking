package com.example.cinema;

import static com.example.cinema.ApiFields.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
class Db {
    final JdbcTemplate jdbc;
    Db(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, index) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                String label = rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT);
                StringBuilder key = new StringBuilder();
                boolean upper = false;
                for (char c : label.toCharArray()) {
                    if (c == '_') { upper = true; continue; }
                    key.append(upper ? Character.toUpperCase(c) : c); upper = false;
                }
                Object value = rs.getObject(i);
                if (value instanceof java.time.OffsetDateTime date) value = date.toInstant();
                row.put(key.toString(), value);
            }
            return row;
        }, args);
    }

    Map<String, Object> one(String sql, Object... args) {
        return rows(sql, args).stream().findFirst().orElseThrow(ApiException::notFound);
    }

    long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{ID});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    static <E extends Enum<E>> E enumValue(Map<String, Object> row, String key, Class<E> type) {
        return Enum.valueOf(type, string(row, key));
    }

    static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    static Instant instant(Map<String, Object> row, String key) { return (Instant) row.get(key); }
    static String string(Map<String, Object> row, String key) { return Objects.toString(row.get(key), null); }
}
