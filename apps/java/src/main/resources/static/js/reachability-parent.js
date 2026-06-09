(function (global) {
  var MESSAGE_TYPE = "exercise-stack-reachability";
  var STACK_TO_TARGET = {
    java: "java",
    rust: "rust",
    python: "python",
    "react-node": "react-node",
    grafana: "grafana",
    kibana: "elk",
    elasticsearch: "elk",
  };

  function mapTarget(stackOrTarget) {
    var key = stackOrTarget != null ? String(stackOrTarget) : "";
    return STACK_TO_TARGET[key] || null;
  }

  function nowMs() {
    return global.performance ? global.performance.now() : Date.now();
  }

  function notifyParent(stackOrTarget, data) {
    if (!global.parent || global.parent === global) return;
    var target = mapTarget(stackOrTarget);
    if (!target) return;
    var status = data && data.status != null ? data.status : undefined;
    var ok = data && data.ok === true;
    if (status == null && data && data.error) ok = false;
    global.parent.postMessage(
      {
        type: MESSAGE_TYPE,
        target: target,
        stack: stackOrTarget != null ? String(stackOrTarget) : undefined,
        status: status,
        ok: ok,
        ms: data && data.ms != null ? data.ms : undefined,
        error: data && data.error != null ? String(data.error) : undefined,
      },
      "*",
    );
  }

  function probePayloadFromPingResult(d, ms) {
    return {
      ok: d && d.ok === true,
      status: d && d.status != null ? d.status : undefined,
      ms: ms,
      error: d && d.error != null ? String(d.error) : undefined,
    };
  }

  function pingSelfHealth() {
    var t0 = nowMs();
    global
      .fetch("/api/observability/health", { method: "GET", credentials: "same-origin" })
      .then(function (res) {
        notifyParent("java", {
          ok: res.ok,
          status: res.status,
          ms: nowMs() - t0,
        });
      })
      .catch(function (err) {
        notifyParent("java", {
          ok: false,
          error: String(err.message || err),
          ms: nowMs() - t0,
        });
      });
  }

  global.exerciseReachabilityParent = {
    notifyParent: notifyParent,
    probePayloadFromPingResult: probePayloadFromPingResult,
    pingSelfHealth: pingSelfHealth,
  };

  if (global.document) {
    global.document.addEventListener("DOMContentLoaded", pingSelfHealth);
  }
})(window);
