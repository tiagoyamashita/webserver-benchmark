/**
 * Full-page sign-in / create-account gate. Hides #app-shell until user is logged in.
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

  function setGateError(message) {
    var el = document.getElementById("gate-auth-error");
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

  function showAuthTab(tab) {
    var loginForm = document.getElementById("gate-login-form");
    var registerForm = document.getElementById("gate-register-form");
    document.querySelectorAll("[data-auth-tab]").forEach(function (btn) {
      var active = btn.getAttribute("data-auth-tab") === tab;
      btn.classList.toggle("active", active);
      btn.setAttribute("aria-selected", active ? "true" : "false");
    });
    setVisible(loginForm, tab === "login", "block");
    setVisible(registerForm, tab === "register", "block");
  }

  function applyAuthGate(data) {
    var gate = document.getElementById("auth-gate");
    var shell = document.getElementById("app-shell");
    if (!gate || !shell) return;
    var loggedIn = isLoggedInSession(data);
    setVisible(gate, !loggedIn, "flex");
    setVisible(shell, loggedIn, "block");
    if (loggedIn && global.exercisesHeaderAuth) {
      global.exercisesHeaderAuth.updateHeaderAuthPanel(data);
    }
  }

  function wireAuthGate(handlers) {
    document.querySelectorAll("[data-auth-tab]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        showAuthTab(btn.getAttribute("data-auth-tab"));
        setGateError("");
      });
    });
    showAuthTab("login");

    var loginForm = document.getElementById("gate-login-form");
    if (loginForm) {
      loginForm.addEventListener("submit", function (ev) {
        ev.preventDefault();
        var email = (document.getElementById("gate-login-email").value || "").trim();
        var password = document.getElementById("gate-login-password").value || "";
        if (!email) {
          setGateError("Email is required.");
          return;
        }
        setGateError("");
        if (handlers && typeof handlers.onLogin === "function") {
          handlers.onLogin(email, password);
        }
      });
    }

    var registerForm = document.getElementById("gate-register-form");
    if (registerForm) {
      registerForm.addEventListener("submit", function (ev) {
        ev.preventDefault();
        var name = (document.getElementById("gate-register-name").value || "").trim();
        var email = (document.getElementById("gate-register-email").value || "").trim();
        var password = document.getElementById("gate-register-password").value || "";
        if (!name || !email || password.length < 8) {
          setGateError("Name, email, and password (min 8 characters) are required.");
          return;
        }
        setGateError("");
        if (handlers && typeof handlers.onRegister === "function") {
          handlers.onRegister(name, email, password);
        }
      });
    }
  }

  global.exercisesAuthGate = {
    isLoggedInSession: isLoggedInSession,
    applyAuthGate: applyAuthGate,
    setGateError: setGateError,
    wireAuthGate: wireAuthGate
  };
})(window);
