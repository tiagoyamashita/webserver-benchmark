const std = @import("std");
const db_mod = @import("../db.zig");
const redis_mod = @import("../redis_client.zig");
const password_mod = @import("password.zig");
const repository = @import("repository.zig");
const session_mod = @import("session.zig");

pub const AuthError = error{
    RedisUnavailable,
    BadRequest,
    NotFound,
    Unauthorized,
    DatabaseUnavailable,
    OutOfMemory,
};

pub const EnsureResult = struct {
    session: session_mod.SharedSession,
    created: bool,
};

pub fn ensureSession(
    client: *redis_mod.Client,
    config: *const session_mod.SessionConfig,
    allocator: std.mem.Allocator,
    client_session_id: ?[]const u8,
) AuthError!EnsureResult {
    if (client_session_id) |id| {
        const trimmed = std.mem.trim(u8, id, " \t\r\n");
        if (trimmed.len > 0) {
            const found = repository.findById(client, config, allocator, trimmed) catch return error.RedisUnavailable;
            if (found) |session| {
                if (!session.isExpired()) {
                    return .{ .session = session, .created = false };
                }
                var expired = session;
                defer expired.deinit(allocator);
                repository.deleteSession(client, config, allocator, trimmed) catch {};
            }
        }
    }
    var session = createGuestSession(allocator) catch return error.OutOfMemory;
    errdefer session.deinit(allocator);
    repository.save(client, config, allocator, &session) catch return error.RedisUnavailable;
    return .{ .session = session, .created = true };
}

pub fn login(
    client: *redis_mod.Client,
    config: *const session_mod.SessionConfig,
    allocator: std.mem.Allocator,
    database: *db_mod.Db,
    email: []const u8,
    password: []const u8,
) AuthError!session_mod.SharedSession {
    const trimmed_email = std.mem.trim(u8, email, " \t\r\n");
    if (trimmed_email.len == 0) return error.BadRequest;
    const auth_row = database.findUserAuthByEmail(allocator, trimmed_email) catch return error.DatabaseUnavailable;
    const row = auth_row orelse return error.NotFound;
    defer db_mod.Db.freeUserAuthRow(row, allocator);
    const hash = row.password_hash orelse return error.Unauthorized;
    if (!password_mod.verifyPassword(password, hash)) return error.Unauthorized;
    var session = createUserSession(allocator, row.id, row.name, row.email) catch return error.OutOfMemory;
    errdefer session.deinit(allocator);
    repository.save(client, config, allocator, &session) catch return error.RedisUnavailable;
    return session;
}

pub fn logout(
    client: *redis_mod.Client,
    config: *const session_mod.SessionConfig,
    allocator: std.mem.Allocator,
    session_id: []const u8,
) AuthError!void {
    repository.deleteSession(client, config, allocator, session_id) catch return error.RedisUnavailable;
}

/// Deletes the current session id (if any), writes a fresh guest session to Redis, and returns it.
pub fn logoutAndCreateGuest(
    client: *redis_mod.Client,
    config: *const session_mod.SessionConfig,
    allocator: std.mem.Allocator,
    session_id: ?[]const u8,
) AuthError!session_mod.SharedSession {
    if (session_id) |id| {
        const trimmed = std.mem.trim(u8, id, " \t\r\n");
        if (trimmed.len > 0) {
            repository.deleteSession(client, config, allocator, trimmed) catch return error.RedisUnavailable;
        }
    }
    var guest = createGuestSession(allocator) catch return error.OutOfMemory;
    errdefer guest.deinit(allocator);
    repository.save(client, config, allocator, &guest) catch return error.RedisUnavailable;
    return guest;
}

pub fn refreshSession(
    client: *redis_mod.Client,
    config: *const session_mod.SessionConfig,
    allocator: std.mem.Allocator,
    current: ?*const session_mod.SharedSession,
) AuthError!session_mod.SharedSession {
    if (current) |existing| {
        repository.deleteSession(client, config, allocator, existing.session_id) catch {};
    }
    var session = if (current) |existing| blk: {
        if (existing.user_id > 0 and existing.email != null) {
            break :blk createUserSession(allocator, existing.user_id, existing.name, existing.email.?)
                catch return error.OutOfMemory;
        }
        break :blk createGuestSession(allocator) catch return error.OutOfMemory;
    } else createGuestSession(allocator) catch return error.OutOfMemory;
    errdefer session.deinit(allocator);
    repository.save(client, config, allocator, &session) catch return error.RedisUnavailable;
    return session;
}

pub fn resolveCurrentSession(
    client: *redis_mod.Client,
    config: *const session_mod.SessionConfig,
    allocator: std.mem.Allocator,
    session_id: []const u8,
) AuthError!?session_mod.SharedSession {
    const trimmed = std.mem.trim(u8, session_id, " \t\r\n");
    if (trimmed.len == 0) return null;
    const session = repository.findById(client, config, allocator, trimmed) catch return error.RedisUnavailable;
    const resolved = session orelse return null;
    if (resolved.isExpired()) {
        var expired = resolved;
        defer expired.deinit(allocator);
        repository.deleteSession(client, config, allocator, trimmed) catch {};
        return null;
    }
    return resolved;
}

fn createGuestSession(allocator: std.mem.Allocator) !session_mod.SharedSession {
    const session_id = try session_mod.newSessionId(allocator);
    const issued_at = try session_mod.utcNowIso(allocator);
    const expires_at = try session_mod.expiresIso(allocator, session_mod.DEFAULT_TTL_SECS);
    return .{
        .session_id = session_id,
        .user_id = 0,
        .email = null,
        .name = try allocator.dupe(u8, "Guest"),
        .issued_at = issued_at,
        .expires_at = expires_at,
        .issuer = try allocator.dupe(u8, session_mod.ISSUER),
    };
}

fn createUserSession(allocator: std.mem.Allocator, user_id: i64, name: []const u8, email: []const u8) !session_mod.SharedSession {
    const session_id = try session_mod.newSessionId(allocator);
    const issued_at = try session_mod.utcNowIso(allocator);
    const expires_at = try session_mod.expiresIso(allocator, session_mod.DEFAULT_TTL_SECS);
    return .{
        .session_id = session_id,
        .user_id = user_id,
        .email = try allocator.dupe(u8, email),
        .name = try allocator.dupe(u8, name),
        .issued_at = issued_at,
        .expires_at = expires_at,
        .issuer = try allocator.dupe(u8, session_mod.ISSUER),
    };
}
