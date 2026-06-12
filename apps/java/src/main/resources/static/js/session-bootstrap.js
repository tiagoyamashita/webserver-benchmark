/**
 * Ensures a shared Redis session exists in the browser (localStorage + httpOnly cookie).
 * Call exercisesSession.ensureSession() once when a dashboard page loads.
 */
(function (global) {
  var STORAGE_KEY = "exercises_session_id";

  function storedSessionId() {
    try {
      return global.localStorage.getItem(STORAGE_KEY) || "";
    } catch (e) {
      return "";
    }
  }

  function setStoredSessionId(sessionId) {
    try {
      if (sessionId) {
        global.localStorage.setItem(STORAGE_KEY, sessionId);
      } else {
        global.localStorage.removeItem(STORAGE_KEY);
      }
    } catch (e) {
      /* ignore */
    }
  }

  function withSessionHeaders(headers) {
    var next = headers || {};
    var sessionId = storedSessionId();
    if (sessionId) {
      next["X-Session-ID"] = sessionId;
      next.Authorization = "Bearer " + sessionId;
    }
    return next;
  }

  function ensureSession() {
    var sessionId = storedSessionId();
    var headers = {
      Accept: "application/json",
      "Content-Type": "application/json"
    };
    if (sessionId) {
      headers["X-Session-ID"] = sessionId;
      headers.Authorization = "Bearer " + sessionId;
    }
    return global
      .fetch("/api/auth/ensure", {
        method: "POST",
        credentials: "same-origin",
        headers: headers,
        body: JSON.stringify(sessionId ? { sessionId: sessionId } : {})
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
          setStoredSessionId("");
          throw new Error(
            "Session ensure failed: HTTP " + r.status + " " + String(r.data || "")
          );
        }
        setStoredSessionId(r.data.sessionId);
        return r.data;
      });
  }

  global.exercisesSession = {
    STORAGE_KEY: STORAGE_KEY,
    storedSessionId: storedSessionId,
    setStoredSessionId: setStoredSessionId,
    withSessionHeaders: withSessionHeaders,
    ensureSession: ensureSession
  };
})(window);
