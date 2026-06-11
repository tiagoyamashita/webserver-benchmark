package com.example.demo.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import net.logstash.logback.composite.AbstractJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;

/** Adds per-request {@code log_seq} and correlation fields to every JSON log line. */
public class ObservabilityJsonProvider extends AbstractJsonProvider<ILoggingEvent> {

  @Override
  public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
    String requestId = RequestIdContext.get();
    if (requestId == null) {
      return;
    }
    JsonWritingUtils.writeNumberField(generator, "log_seq", RequestIdContext.nextLogSeq());
    String page = DashboardPageContext.get();
    if (page != null) {
      JsonWritingUtils.writeStringField(generator, "dashboard_page", page);
    }
  }
}
