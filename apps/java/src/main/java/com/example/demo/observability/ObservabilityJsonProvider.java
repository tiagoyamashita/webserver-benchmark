package com.example.demo.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.demo.auth.SessionContext;
import com.example.demo.auth.SharedSession;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import net.logstash.logback.composite.AbstractJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;

/** Adds per-request {@code log_seq}, {@code session_id}, and correlation fields to JSON log lines. */
public class ObservabilityJsonProvider extends AbstractJsonProvider<ILoggingEvent> {

  @Override
  public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
    String requestId = RequestIdContext.get();
    if (requestId != null) {
      JsonWritingUtils.writeNumberField(generator, "log_seq", RequestIdContext.nextLogSeq());
      String page = DashboardPageContext.get();
      if (page != null) {
        JsonWritingUtils.writeStringField(generator, "dashboard_page", page);
      }
    }
    SharedSession session = SessionContext.get();
    if (session != null) {
      String sessionId = session.sessionId();
      if (sessionId != null && !sessionId.isBlank()) {
        JsonWritingUtils.writeStringField(generator, "session_id", sessionId);
      }
    }
  }
}

