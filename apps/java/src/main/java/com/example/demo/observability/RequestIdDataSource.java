package com.example.demo.observability;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Stamps Postgres {@code application_name} with the active HTTP request and session ids. */
public class RequestIdDataSource extends DelegatingDataSource {

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
    PostgresCorrelation.stampConnection(connection);
    return connection;
  }
}
