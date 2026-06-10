package com.example.demo.observability;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Stamps Postgres {@code application_name} with the active HTTP request id. */
public class RequestIdDataSource extends DelegatingDataSource {

  private static final String SERVICE = "exercises-java";

  public RequestIdDataSource(DataSource target) {
    super(target);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return stamp(super.getConnection());
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return stamp(super.getConnection(username, password));
  }

  private static Connection stamp(Connection connection) throws SQLException {
    String requestId = RequestIdContext.get();
    if (requestId == null) {
      return connection;
    }
    String appName = postgresApplicationName(SERVICE, requestId);
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("SET application_name TO '" + escape(appName) + "'");
    }
    return connection;
  }

  static String postgresApplicationName(String service, String requestId) {
    String value = service + ";req=" + requestId;
    return value.length() <= 63 ? value : value.substring(0, 63);
  }

  private static String escape(String value) {
    return value.replace("'", "''");
  }
}
