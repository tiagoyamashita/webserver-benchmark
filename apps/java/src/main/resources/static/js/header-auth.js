/**
 * Top-right login form / logged-in user panel for exercises dashboards.
 * Requires #header-login-form, #header-user-panel, and host app session helpers.
 */
(function (global) {
  function isLoggedInSession(data) {
    return !!(data && data.sessionId && data.userId > 0 && data.email);
  }

  function setVisible(el, visible, displayMode) {
    if (!el) return;
    el.hidden = !visible;
    el.style.display = visible ? displayMode : "none";
  }

  function setLoginError(message) {
    var el = document.getElementById("header-login-error");
    if (!el) return;
    if (!message) {
      el.textContent = "";
      el.hidden = true;
      el.style.display = "none";
      return;
    }
    el.textContent = message;
    el.hidden = false;
    el.style.display = "block";
  }

  function updateHeaderAuthPanel(data) {
    var form = document.getElementById("header-login-form");
    var userPanel = document.getElementById("header-user-panel");
    var loading = document.getElementById("header-auth-loading");
    var label = document.getElementById("header-user-label");
    if (!form || !userPanel) return;
    setVisible(loading, false, "block");
    if (isLoggedInSession(data)) {
      setLoginError("");
      setVisible(form, false, "flex");
      setVisible(userPanel, true, "flex");
      if (label) {
        label.textContent = String(data.name || "User") + " · " + String(data.email);
      }
      return;
    }
    setVisible(userPanel, false, "flex");
    setVisible(form, true, "flex");
  }

  function wireLoginForm(submitLogin) {
    var form = document.getElementById("header-login-form");
    if (!form || typeof submitLogin !== "function") return;
    form.addEventListener("submit", function (ev) {
      ev.preventDefault();
      var emailEl = document.getElementById("header-login-email");
      var passwordEl = document.getElementById("header-login-password");
      var email = emailEl && emailEl.value ? emailEl.value.trim() : "";
      var password = passwordEl && passwordEl.value ? passwordEl.value : "";
      if (!email) {
        setLoginError("Email is required.");
        return;
      }
      setLoginError("");
      submitLogin(email, password);
    });
  }

  function wireLogoutButton(performLogout) {
    var btn = document.getElementById("header-logout-btn");
    if (!btn || typeof performLogout !== "function") return;
    btn.addEventListener("click", function () {
      performLogout();
    });
  }

  global.exercisesHeaderAuth = {
    isLoggedInSession: isLoggedInSession,
    updateHeaderAuthPanel: updateHeaderAuthPanel,
    setLoginError: setLoginError,
    wireLoginForm: wireLoginForm,
    wireLogoutButton: wireLogoutButton
  };
})(window);
