package com.example.pipeline.service.source;

import com.example.pipeline.model.Pipeline;
import com.example.pipeline.model.SourceConfig;
import com.example.pipeline.util.EnvVarResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Database source handler that streams query results.
 */
@Component
public class DatabaseSourceHandler implements SourceHandler {
    private final Map<String, HikariDataSource> dsCache = new ConcurrentHashMap<>();

    @Override
    public String getType() {
        return "database";
    }

    @SuppressWarnings("unchecked")
    @Override
    public void fetchRowsAndProcess(Pipeline pipeline, Consumer<Map<String, Object>> rowConsumer) throws Exception {
        SourceConfig src = pipeline.getSource();
        Map<String,Object> rawConfig = src.getConfig();
        Map<String,Object> config = EnvVarResolver.resolveMap(rawConfig);

        Map<String,Object> conn = (Map<String,Object>) config.get("connection");
        if (conn == null) throw new IllegalArgumentException("database source requires 'connection' object");

        String driver = asString(conn.get("driver"));
        String host = asString(conn.get("host"));
        Integer port = conn.get("port") != null ? ((Number)conn.get("port")).intValue() : null;
        String database = asString(conn.get("database"));
        String username = asString(conn.get("username"));
        String password = asString(conn.get("password"));
        String jdbcUrl = asString(conn.get("jdbc_url"));

        // Build jdbcUrl before using in lambda, so it is effectively final
        if ((jdbcUrl == null || jdbcUrl.isEmpty()) && driver != null) {
            if ("postgresql".equalsIgnoreCase(driver)) {
                if (port == null) port = 5432;
                jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            } else if ("mysql".equalsIgnoreCase(driver)) {
                if (port == null) port = 3306;
                jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true";
            } else {
                throw new IllegalArgumentException("Unsupported driver: " + driver + ". Provide jdbc_url for custom drivers.");
            }
        }

        final String finalJdbcUrl = jdbcUrl;
        String poolKey = username + "@" + finalJdbcUrl;
        HikariDataSource ds = dsCache.computeIfAbsent(poolKey, k -> {
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl(finalJdbcUrl);
            if (username != null) hc.setUsername(username);
            if (password != null) hc.setPassword(password);
            hc.setMaximumPoolSize(5);
            hc.setConnectionTimeout(10000);
            return new HikariDataSource(hc);
        });

        JdbcTemplate jdbc = new JdbcTemplate(ds);

        String query = asString(config.get("query"));
        if (query == null || query.trim().isEmpty()) throw new IllegalArgumentException("database source requires 'query'");

        int fetchSize = ((Number) config.getOrDefault("fetch_size", 500)).intValue();

        jdbc.query(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(query, java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
            try {
                ps.setFetchSize(fetchSize);
            } catch (Exception ignored) {}
            return ps;
        }, (ResultSet rs) -> {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            while (rs.next()) {
                Map<String,Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    String label = md.getColumnLabel(i);
                    if (label == null || label.isEmpty()) label = md.getColumnName(i);
                    row.put(label, rs.getObject(i));
                }
                rowConsumer.accept(row);
            }
            return null;
        });
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
