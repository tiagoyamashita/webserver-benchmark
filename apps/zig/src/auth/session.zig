const std = @import("std");

pub const ISSUER = "zig";
pub const DEFAULT_TTL_SECS: u64 = 86_400;

pub const SessionConfig = struct {
    redis_key_prefix: []const u8,
    ttl_secs: u64,
    cookie_name: []const u8,

    pub fn fromEnv(allocator: std.mem.Allocator) SessionConfig {
        return .{
            .redis_key_prefix = dupEnv(allocator, "WEBSERVER_BENCHMARK_SESSION_REDIS_PREFIX", "webserver-benchmark:session:"),
            .ttl_secs = DEFAULT_TTL_SECS,
            .cookie_name = dupEnv(allocator, "WEBSERVER_BENCHMARK_SESSION_COOKIE", "webserver_benchmark_session"),
        };
    }

    pub fn deinit(self: *SessionConfig, allocator: std.mem.Allocator) void {
        allocator.free(self.redis_key_prefix);
        allocator.free(self.cookie_name);
    }

    pub fn redisKey(self: *const SessionConfig, allocator: std.mem.Allocator, session_id: []const u8) ![]const u8 {
        return std.fmt.allocPrint(allocator, "{s}{s}", .{ self.redis_key_prefix, session_id });
    }
};

pub const SharedSession = struct {
    session_id: []const u8,
    user_id: i64,
    email: ?[]const u8,
    name: []const u8,
    issued_at: []const u8,
    expires_at: []const u8,
    issuer: []const u8,

    pub fn deinit(self: *SharedSession, allocator: std.mem.Allocator) void {
        allocator.free(self.session_id);
        if (self.email) |email| allocator.free(email);
        allocator.free(self.name);
        allocator.free(self.issued_at);
        allocator.free(self.expires_at);
        allocator.free(self.issuer);
    }

    pub fn isExpired(self: *const SharedSession) bool {
        var now_buf: [32]u8 = undefined;
        const now_iso = utcNowBuf(&now_buf);
        return std.mem.order(u8, now_iso, self.expires_at) == .gt;
    }

    pub fn toJson(self: *const SharedSession, allocator: std.mem.Allocator) ![]const u8 {
        if (self.email) |email| {
            return std.fmt.allocPrint(
                allocator,
                "{{\"sessionId\":\"{s}\",\"userId\":{d},\"email\":\"{s}\",\"name\":\"{s}\",\"issuedAt\":\"{s}\",\"expiresAt\":\"{s}\",\"issuer\":\"{s}\"}}",
                .{ self.session_id, self.user_id, email, self.name, self.issued_at, self.expires_at, self.issuer },
            );
        }
        return std.fmt.allocPrint(
            allocator,
            "{{\"sessionId\":\"{s}\",\"userId\":{d},\"name\":\"{s}\",\"issuedAt\":\"{s}\",\"expiresAt\":\"{s}\",\"issuer\":\"{s}\"}}",
            .{ self.session_id, self.user_id, self.name, self.issued_at, self.expires_at, self.issuer },
        );
    }

    pub fn toResponseJson(self: *const SharedSession, allocator: std.mem.Allocator, redis_key: []const u8) ![]const u8 {
        if (self.email) |email| {
            return std.fmt.allocPrint(
                allocator,
                "{{\"sessionId\":\"{s}\",\"userId\":{d},\"email\":\"{s}\",\"name\":\"{s}\",\"issuedAt\":\"{s}\",\"expiresAt\":\"{s}\",\"issuer\":\"{s}\",\"redisKey\":\"{s}\"}}",
                .{ self.session_id, self.user_id, email, self.name, self.issued_at, self.expires_at, self.issuer, redis_key },
            );
        }
        return std.fmt.allocPrint(
            allocator,
            "{{\"sessionId\":\"{s}\",\"userId\":{d},\"name\":\"{s}\",\"issuedAt\":\"{s}\",\"expiresAt\":\"{s}\",\"issuer\":\"{s}\",\"redisKey\":\"{s}\"}}",
            .{ self.session_id, self.user_id, self.name, self.issued_at, self.expires_at, self.issuer, redis_key },
        );
    }

    pub fn parse(allocator: std.mem.Allocator, json: []const u8) !SharedSession {
        const session_id = try requireJsonString(allocator, json, "sessionId");
        const user_id = parseJsonInt(json, "userId") orelse 0;
        const email = try optionalJsonString(allocator, json, "email");
        const name = try requireJsonString(allocator, json, "name");
        const issued_at = try requireJsonString(allocator, json, "issuedAt");
        const expires_at = try requireJsonString(allocator, json, "expiresAt");
        const issuer = try requireJsonString(allocator, json, "issuer");
        return .{
            .session_id = session_id,
            .user_id = user_id,
            .email = email,
            .name = name,
            .issued_at = issued_at,
            .expires_at = expires_at,
            .issuer = issuer,
        };
    }
};

pub fn newSessionId(allocator: std.mem.Allocator) ![]const u8 {
    var bytes: [16]u8 = undefined;
    std.crypto.random.bytes(&bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    return std.fmt.allocPrint(
        allocator,
        "{x:0>8}-{x:0>4}-{x:0>4}-{x:0>4}-{x:0>12}",
        .{
            std.mem.readInt(u32, bytes[0..4], .big),
            std.mem.readInt(u16, bytes[4..6], .big),
            std.mem.readInt(u16, bytes[6..8], .big),
            std.mem.readInt(u16, bytes[8..10], .big),
            std.mem.readInt(u48, bytes[10..16], .big),
        },
    );
}

pub fn utcNowIso(allocator: std.mem.Allocator) ![]const u8 {
    var buf: [32]u8 = undefined;
    return try allocator.dupe(u8, utcNowBuf(&buf));
}

fn utcNowBuf(buf: *[32]u8) []const u8 {
    const secs = std.time.timestamp();
    const epoch_sec = std.time.epoch.EpochSeconds{ .secs = @intCast(secs) };
    const epoch_day = epoch_sec.getEpochDay();
    const day_sec = epoch_sec.getDaySeconds();
    const year_day = epoch_day.calculateYearDay();
    const month_day = year_day.calculateMonthDay();
    const ms: i64 = std.time.milliTimestamp() - @as(i64, @intCast(secs)) * 1000;
    return std.fmt.bufPrint(buf, "{d:0>4}-{d:0>2}-{d:0>2}T{d:0>2}:{d:0>2}:{d:0>2}.{d:0>3}Z", .{
        year_day.year,
        month_day.month.numeric(),
        month_day.day_index + 1,
        day_sec.getHoursIntoDay(),
        day_sec.getMinutesIntoHour(),
        day_sec.getSecondsIntoMinute(),
        @as(u16, @intCast(@max(ms, 0))),
    }) catch "1970-01-01T00:00:00.000Z";
}

pub fn expiresIso(allocator: std.mem.Allocator, ttl_secs: u64) ![]const u8 {
    const future = std.time.timestamp() + @as(i64, @intCast(ttl_secs));
    const epoch_sec = std.time.epoch.EpochSeconds{ .secs = @intCast(future) };
    const epoch_day = epoch_sec.getEpochDay();
    const day_sec = epoch_sec.getDaySeconds();
    const year_day = epoch_day.calculateYearDay();
    const month_day = year_day.calculateMonthDay();
    return std.fmt.allocPrint(allocator, "{d:0>4}-{d:0>2}-{d:0>2}T{d:0>2}:{d:0>2}:{d:0>2}.000Z", .{
        year_day.year,
        month_day.month.numeric(),
        month_day.day_index + 1,
        day_sec.getHoursIntoDay(),
        day_sec.getMinutesIntoHour(),
        day_sec.getSecondsIntoMinute(),
    });
}

fn dupEnv(allocator: std.mem.Allocator, key: []const u8, default: []const u8) []const u8 {
    if (std.posix.getenv(key)) |value| {
        const trimmed = std.mem.trim(u8, value, " \t\r\n");
        if (trimmed.len > 0) return allocator.dupe(u8, trimmed) catch default;
    }
    return allocator.dupe(u8, default) catch default;
}

fn jsonKeyPos(json: []const u8, key: []const u8) ?usize {
    var needle_buf: [64]u8 = undefined;
    const needle = std.fmt.bufPrint(&needle_buf, "\"{s}\"", .{key}) catch return null;
    return std.mem.indexOf(u8, json, needle);
}

fn requireJsonString(allocator: std.mem.Allocator, json: []const u8, key: []const u8) ![]const u8 {
    const value = try optionalJsonString(allocator, json, key);
    return value orelse error.InvalidSessionJson;
}

fn optionalJsonString(allocator: std.mem.Allocator, json: []const u8, key: []const u8) !?[]const u8 {
    const key_pos = jsonKeyPos(json, key) orelse return null;
    const after_key = json[key_pos + key.len + 2 ..];
    const colon = std.mem.indexOfScalar(u8, after_key, ':') orelse return null;
    var rest = std.mem.trimLeft(u8, after_key[colon + 1 ..], " \t");
    if (rest.len == 0) return null;
    if (rest[0] == 'n') return null;
    if (rest[0] != '"') return error.InvalidSessionJson;
    rest = rest[1..];
    const end = std.mem.indexOfScalar(u8, rest, '"') orelse return error.InvalidSessionJson;
    return try allocator.dupe(u8, rest[0..end]);
}

fn parseJsonInt(json: []const u8, key: []const u8) ?i64 {
    const key_pos = jsonKeyPos(json, key) orelse return null;
    const after_key = json[key_pos + key.len + 2 ..];
    const colon = std.mem.indexOfScalar(u8, after_key, ':') orelse return null;
    const rest = std.mem.trimLeft(u8, after_key[colon + 1 ..], " \t");
    var end: usize = 0;
    while (end < rest.len and (std.ascii.isDigit(rest[end]) or rest[end] == '-')) : (end += 1) {}
    return std.fmt.parseInt(i64, rest[0..end], 10) catch null;
}

pub const InvalidSessionJson = error{InvalidSessionJson};
