const std = @import("std");
const app_mod = @import("../app.zig");
const auth_service = @import("service.zig");
const cookies = @import("cookies.zig");
const http = @import("../http_response.zig");
const session_mod = @import("session.zig");

pub fn isPublicPath(method: std.http.Method, path: []const u8) bool {
    if (method == .GET and (std.mem.eql(u8, path, "/") or std.mem.eql(u8, path, "/health") or std.mem.eql(u8, path, "/metrics"))) {
        return true;
    }
    if (std.mem.startsWith(u8, path, "/api/auth/")) return true;
    if (method == .POST and std.mem.eql(u8, path, "/api/users")) return true;
    return false;
}

pub fn isLoggedIn(session: *const session_mod.SharedSession) bool {
    return session.user_id > 0 and session.email != null;
}

/// Returns true when the request may proceed. Writes 401/503 and returns false when blocked.
pub fn requireLoggedIn(app: *app_mod.App, request: *std.http.Server.Request) !bool {
    const allocator = app.allocator;
    const cookie = try cookies.readSessionCookie(allocator, request, app.session_config.cookie_name);
    defer allocator.free(cookie);

    var client = app.redis orelse {
        try http.writeJsonResponse(
            request,
            .service_unavailable,
            "{\"error\":\"Redis session store not configured\"}",
        );
        return false;
    };

    app.redis_mutex.lock();
    defer app.redis_mutex.unlock();

    if (cookie.len == 0) {
        try http.writeJsonResponse(request, .unauthorized, "{\"error\":\"Sign in required\"}");
        return false;
    }

    const current = auth_service.resolveCurrentSession(&client, &app.session_config, allocator, cookie) catch {
        try http.writeJsonResponse(request, .unauthorized, "{\"error\":\"Redis error\"}");
        return false;
    };
    if (current) |resolved| {
        var session = resolved;
        defer session.deinit(allocator);
        if (isLoggedIn(&session)) return true;
    }

    try http.writeJsonResponse(request, .unauthorized, "{\"error\":\"Sign in required\"}");
    return false;
}
