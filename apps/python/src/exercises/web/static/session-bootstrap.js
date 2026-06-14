/**
 * Ensures a shared Redis session via HttpOnly cookie only (no localStorage).
 * Call exercisesSession.ensureSession() once when a dashboard page loads.
 */
(function (global) {
  var LEGACY_STORAGE_KEY = "exercises_session_id";

  function clearLegacyStoredSessionId() {
    try {
      global.localStorage.removeItem(LEGACY_STORAGE_KEY);
    } catch (e) {
      /* ignore */
    }
  }

  function newRequestId() {
    if (typeof crypto !== "undefined" && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return "req-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 10);
  }

  /** Session id is sent only via HttpOnly cookie; do not add Bearer / X-Session-ID from JS. */
  function withSessionHeaders(headers) {
    return headers || {};
  }

  function ensureSession() {
    clearLegacyStoredSessionId();
    return global
      .fetch("/api/auth/ensure", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          "X-Request-ID": newRequestId()
        },
        body: "{}"
      })
      .then(function (res) {
        return res.text().then(function (text) {
          var data = text;
          try {
            data = JSON.parse(text);
          } catch (e) {
            /* plain error */
          }
          return { ok: res.ok, status: res.status, data: data };
        });
      })
      .then(function (r) {
        if (!r.ok || !r.data || !r.data.sessionId) {
          throw new Error(
            "Session ensure failed: HTTP " + r.status + " " + String(r.data || "")
          );
        }
        return r.data;
      });
  }

  clearLegacyStoredSessionId();

  global.exercisesSession = {
    withSessionHeaders: withSessionHeaders,
    ensureSession: ensureSession
  };
})(window);
