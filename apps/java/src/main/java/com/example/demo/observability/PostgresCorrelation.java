package com.example.demo.observability;

import com.example.demo.auth.SessionContext;
import com.example.demo.auth.SharedSession;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Maps HTTP {@code X-Request-ID} into Postgres {@code application_name}. */
public final class PostgresCorrelation {

  static final String SERVICE = "webserver-benchmark-java";
  private static final int POSTGRES_APPLICATION_NAME_MAX_LEN = 63;

  private PostgresCorrelation() {}

  public static String resolveRequestId() {
    String requestId = RequestIdContext.get();
    if (requestId == null || requestId.isBlank()) {
      return null;
    }
    return requestId.trim();
  }

  public static String resolveSessionId() {
    SharedSession session = SessionContext.get();
    if (session == null) {
      return null;
    }
    String sessionId = session.sessionId();
    if (sessionId == null || sessionId.isBlank()) {
      return null;
    }
    return sessionId.trim();
  }

  /** Stamp pooled or ad-hoc JDBC connections before SQL (GET list, stack ping {@code SELECT 1}, etc.). */
  public static void stampConnection(Connection connection) throws SQLException {
    stampApplicationName(connection, resolveEffectiveRequestId());
  }

  static String resolveEffectiveRequestId() {
    String requestId = resolveRequestId();
    if (requestId != null) {
      return requestId;
    }
    return RequestIdRelay.resolveOutboundRequestId();
  }

  static void stampApplicationName(Connection connection, String requestId) throws SQLException {
    String appName =
        requestId != null ? postgresApplicationName(SERVICE, requestId) : SERVICE;
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("SET application_name TO '" + escapeSqlLiteral(appName) + "'");
    }
  }

  static String postgresApplicationName(String service, String requestId) {
    if (requestId == null || requestId.isBlank()) {
      return service;
    }
    String stamped = service + ";req=" + requestId.trim();
    if (stamped.length() <= POSTGRES_APPLICATION_NAME_MAX_LEN) {
      return stamped;
    }
    return stamped.substring(0, POSTGRES_APPLICATION_NAME_MAX_LEN);
  }

  private static String escapeSqlLiteral(String value) {
    return value.replace("'", "''");
  }
}
