const std = @import("std");
const app_mod = @import("../app.zig");
const auth_service = @import("../auth/service.zig");
const cookies = @import("../auth/cookies.zig");
const session_mod = @import("../auth/session.zig");
const http = @import("../http_response.zig");
const ctrl_log = @import("../controller_logging.zig");

const SOURCE = "src/handlers/auth.zig";

pub fn handleEnsure(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("ensure_session", SOURCE, "POST", "/api/auth/ensure");
    const allocator = app.allocator;

    const cookie = try cookies.readSessionCookie(allocator, request, app.session_config.cookie_name);
    defer allocator.free(cookie);

    const body = try http.readBody(allocator, request);
    defer allocator.free(body);

    const body_session_id = cookies.extractJsonString(allocator, body, "sessionId");
    defer if (body_session_id) |id| allocator.free(id);

    var client = app.redis orelse {
        return try http.writeJsonResponse(
            request,
            .service_unavailable,
            "{\"error\":\"Redis session store not configured\"}",
        );
    };
    app.redis_mutex.lock();
    defer app.redis_mutex.unlock();

    const preferred_id = if (cookie.len > 0)
        cookie
    else if (body_session_id) |id|
        id
    else
        "";

    const result = auth_service.ensureSession(
        &client,
        &app.session_config,
        allocator,
        if (preferred_id.len > 0) preferred_id else null,
    ) catch |err| {
        return try mapAuthError(request, err, "ensure_session");
    };
    var session = result.session;
    defer session.deinit(allocator);

    ctrl_log.logSucceeded("ensure_session", SOURCE, "session_id={s} created={}", .{
        session.session_id,
        result.created,
    });
    return cookies.writeSessionJson(request, allocator, &app.session_config, &session, true);
}

pub fn handleLogin(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("login", SOURCE, "POST", "/api/auth/login");
    const allocator = app.allocator;

    const body = try http.readBody(allocator, request);
    defer allocator.free(body);

    const email = cookies.extractJsonString(allocator, body, "email");
    defer if (email) |value| allocator.free(value);
    const password = cookies.extractJsonString(allocator, body, "password");
    defer if (password) |value| allocator.free(value);

    const email_text = email orelse {
        return try http.writeJsonResponse(request, .bad_request, "{\"error\":\"email and password are required\"}");
    };
    const password_text = password orelse {
        return try http.writeJsonResponse(request, .bad_request, "{\"error\":\"email and password are required\"}");
    };

    var database = app.db orelse {
        return try http.writeJsonResponse(request, .service_unavailable, "{\"error\":\"Postgres not configured\"}");
    };
    var client = app.redis orelse {
        return try http.writeJsonResponse(
            request,
            .service_unavailable,
            "{\"error\":\"Redis session store not configured\"}",
        );
    };

    app.db_mutex.lock();
    defer app.db_mutex.unlock();
    app.redis_mutex.lock();
    defer app.redis_mutex.unlock();

    var session = auth_service.login(
        &client,
        &app.session_config,
        allocator,
        &database,
        email_text,
        password_text,
    ) catch |err| {
        return try mapAuthError(request, err, "login");
    };
    defer session.deinit(allocator);

    ctrl_log.logSucceeded("login", SOURCE, "session_id={s} user_id={d}", .{
        session.session_id,
        session.user_id,
    });
    return cookies.writeSessionJson(request, allocator, &app.session_config, &session, true);
}

pub fn handleLogout(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("logout", SOURCE, "POST", "/api/auth/logout");
    const allocator = app.allocator;

    const cookie = try cookies.readSessionCookie(allocator, request, app.session_config.cookie_name);
    defer allocator.free(cookie);

    var client = app.redis orelse {
        return try http.writeJsonResponse(
            request,
            .service_unavailable,
            "{\"error\":\"Redis session store not configured\"}",
        );
    };
    app.redis_mutex.lock();
    defer app.redis_mutex.unlock();

    const previous_id: ?[]const u8 = if (cookie.len > 0) cookie else null;
    var guest = auth_service.logoutAndCreateGuest(
        &client,
        &app.session_config,
        allocator,
        previous_id,
    ) catch |err| {
        return try mapAuthError(request, err, "logout");
    };
    defer guest.deinit(allocator);

    if (previous_id) |id| {
        ctrl_log.logSucceeded("logout", SOURCE, "previous_session_id={s} guest_session_id={s}", .{ id, guest.session_id });
    } else {
        ctrl_log.logSucceeded("logout", SOURCE, "guest_session_id={s}", .{guest.session_id});
    }

    return cookies.writeSessionJson(request, allocator, &app.session_config, &guest, true);
}

pub fn handleRefresh(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("refresh_session", SOURCE, "POST", "/api/auth/refresh");
    const allocator = app.allocator;

    const cookie = try cookies.readSessionCookie(allocator, request, app.session_config.cookie_name);
    defer allocator.free(cookie);

    var client = app.redis orelse {
        return try http.writeJsonResponse(
            request,
            .service_unavailable,
            "{\"error\":\"Redis session store not configured\"}",
        );
    };
    app.redis_mutex.lock();
    defer app.redis_mutex.unlock();

    var current_session: ?session_mod.SharedSession = null;
    if (cookie.len > 0) {
        current_session = auth_service.resolveCurrentSession(&client, &app.session_config, allocator, cookie) catch |err| {
            return try mapAuthError(request, err, "refresh_session");
        };
    }
    defer if (current_session) |*existing| existing.deinit(allocator);

    var session = auth_service.refreshSession(
        &client,
        &app.session_config,
        allocator,
        if (current_session) |*existing| existing else null,
    ) catch |err| {
        return try mapAuthError(request, err, "refresh_session");
    };
    defer session.deinit(allocator);

    ctrl_log.logSucceeded("refresh_session", SOURCE, "session_id={s} user_id={d}", .{
        session.session_id,
        session.user_id,
    });
    return cookies.writeSessionJson(request, allocator, &app.session_config, &session, true);
}

pub fn handleSession(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("current_session", SOURCE, "GET", "/api/auth/session");
    const allocator = app.allocator;

    const cookie = try cookies.readSessionCookie(allocator, request, app.session_config.cookie_name);
    defer allocator.free(cookie);

    var client = app.redis orelse {
        return try http.writeJsonResponse(
            request,
            .service_unavailable,
            "{\"error\":\"Redis session store not configured\"}",
        );
    };
    app.redis_mutex.lock();
    defer app.redis_mutex.unlock();

    if (cookie.len == 0) {
        return try http.writeJsonResponse(request, .unauthorized, "{\"error\":\"No active session\"}");
    }

    const current = auth_service.resolveCurrentSession(&client, &app.session_config, allocator, cookie) catch |err| {
        return try mapAuthError(request, err, "current_session");
    };
    var session = current orelse {
        return try http.writeJsonResponse(request, .unauthorized, "{\"error\":\"No active session\"}");
    };
    defer session.deinit(allocator);

    if (session.isExpired()) {
        return try http.writeJsonResponse(request, .unauthorized, "{\"error\":\"Session expired\"}");
    }

    ctrl_log.logSucceeded("current_session", SOURCE, "session_id={s} user_id={d}", .{
        session.session_id,
        session.user_id,
    });
    return cookies.writeSessionJson(request, allocator, &app.session_config, &session, false);
}

fn mapAuthError(request: *std.http.Server.Request, err: auth_service.AuthError, action: []const u8) !void {
    const status: std.http.Status = switch (err) {
        error.BadRequest => .bad_request,
        error.NotFound => .not_found,
        error.Unauthorized => .unauthorized,
        error.DatabaseUnavailable => .internal_server_error,
        error.RedisUnavailable => .service_unavailable,
        error.OutOfMemory => .internal_server_error,
    };
    const body: []const u8 = switch (err) {
        error.BadRequest => "{\"error\":\"Invalid request\"}",
        error.NotFound => "{\"error\":\"User not found\"}",
        error.Unauthorized => "{\"error\":\"Invalid email or password\"}",
        error.DatabaseUnavailable => "{\"error\":\"Database error\"}",
        error.RedisUnavailable => "{\"error\":\"Redis error\"}",
        error.OutOfMemory => "{\"error\":\"Out of memory\"}",
    };
    ctrl_log.logWarn(action, SOURCE, "error={s}", .{@errorName(err)});
    return try http.writeJsonResponse(request, status, body);
}
