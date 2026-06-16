const std = @import("std");

pub const RedisError = error{
    ConnectionFailed,
    InvalidUrl,
    ProtocolError,
    UnexpectedResponse,
    OutOfMemory,
};

pub const Client = struct {
    stream: std.net.Stream,
    allocator: std.mem.Allocator,

    pub fn connect(allocator: std.mem.Allocator, host: []const u8, port: u16) RedisError!Client {
        const stream = std.net.tcpConnectToHost(allocator, host, port) catch return error.ConnectionFailed;
        return .{ .stream = stream, .allocator = allocator };
    }

    pub fn connectFromEnv(allocator: std.mem.Allocator) RedisError!?Client {
        const url = std.posix.getenv("REDIS_URL");
        if (url) |raw| {
            const trimmed = std.mem.trim(u8, raw, " \t\r\n");
            if (trimmed.len > 0) {
                return try connectUrl(allocator, trimmed);
            }
        }
        const host = std.posix.getenv("REDIS_HOST") orelse return null;
        const host_trimmed = std.mem.trim(u8, host, " \t\r\n");
        if (host_trimmed.len == 0) return null;
        const port = parsePortEnv("REDIS_PORT", 6379);
        return try connect(allocator, host_trimmed, port);
    }

    pub fn deinit(self: *Client) void {
        self.stream.close();
    }

    pub fn ping(self: *Client) RedisError!void {
        try self.sendCommand(&.{ "PING" });
        const reply = try self.readReply(self.allocator);
        defer reply.deinit(self.allocator);
        if (reply.kind != .simple or !std.mem.eql(u8, reply.simple, "PONG")) {
            return error.UnexpectedResponse;
        }
    }

    pub fn get(self: *Client, allocator: std.mem.Allocator, key: []const u8) RedisError!?[]const u8 {
        try self.sendCommand(&.{ "GET", key });
        const reply = try self.readReply(allocator);
        defer reply.deinit(allocator);
        return switch (reply.kind) {
            .bulk => if (reply.bulk) |value| try allocator.dupe(u8, value) else null,
            .null_bulk => null,
            else => error.UnexpectedResponse,
        };
    }

    pub fn setEx(self: *Client, key: []const u8, value: []const u8, ttl_secs: u64) RedisError!void {
        var ttl_buf: [32]u8 = undefined;
        const ttl_text = std.fmt.bufPrint(&ttl_buf, "{d}", .{ttl_secs}) catch return error.ProtocolError;
        try self.sendCommand(&.{ "SET", key, value, "EX", ttl_text });
        const reply = try self.readReply(self.allocator);
        defer reply.deinit(self.allocator);
        if (reply.kind != .simple or !std.mem.eql(u8, reply.simple, "OK")) {
            return error.UnexpectedResponse;
        }
    }

    pub fn del(self: *Client, key: []const u8) RedisError!void {
        try self.sendCommand(&.{ "DEL", key });
        const reply = try self.readReply(self.allocator);
        defer reply.deinit(self.allocator);
        if (reply.kind != .integer) return error.UnexpectedResponse;
    }

    fn connectUrl(allocator: std.mem.Allocator, url: []const u8) RedisError!Client {
        const scheme_end = std.mem.indexOf(u8, url, "://") orelse return error.InvalidUrl;
        if (!std.mem.eql(u8, url[0..scheme_end], "redis")) return error.InvalidUrl;
        const rest = url[scheme_end + 3 ..];
        const slash = std.mem.indexOfScalar(u8, rest, '/');
        const host_port = if (slash) |idx| rest[0..idx] else rest;
        const colon = std.mem.lastIndexOfScalar(u8, host_port, ':');
        if (colon) |c| {
            const host = host_port[0..c];
            const port = std.fmt.parseInt(u16, host_port[c + 1 ..], 10) catch return error.InvalidUrl;
            return connect(allocator, host, port);
        }
        return connect(allocator, host_port, 6379);
    }

    fn sendCommand(self: *Client, parts: []const []const u8) RedisError!void {
        var list = std.ArrayList(u8).init(self.allocator);
        defer list.deinit();
        try std.fmt.format(list.writer(), "*{d}\r\n", .{parts.len});
        for (parts) |part| {
            try std.fmt.format(list.writer(), "${d}\r\n{s}\r\n", .{ part.len, part });
        }
        self.stream.writeAll(list.items) catch return error.ConnectionFailed;
    }

    const Reply = struct {
        kind: enum { simple, err, integer, bulk, null_bulk },
        simple: []const u8 = "",
        integer: i64 = 0,
        bulk: ?[]const u8 = null,

        fn deinit(self: Reply, allocator: std.mem.Allocator) void {
            if (self.bulk) |value| allocator.free(value);
        }
    };

    fn readLine(self: *Client, allocator: std.mem.Allocator) RedisError![]const u8 {
        var list = std.ArrayList(u8).init(allocator);
        errdefer list.deinit();
        while (true) {
            var byte: [1]u8 = undefined;
            const n = self.stream.read(&byte) catch return error.ConnectionFailed;
            if (n == 0) return error.ConnectionFailed;
            try list.append(byte[0]);
            if (list.items.len >= 2 and list.items[list.items.len - 2] == '\r' and list.items[list.items.len - 1] == '\n') {
                return list.toOwnedSlice();
            }
            if (list.items.len > 8192) return error.ProtocolError;
        }
    }

    fn readFully(self: *Client, buffer: []u8) RedisError!void {
        var index: usize = 0;
        while (index < buffer.len) {
            const n = self.stream.read(buffer[index..]) catch return error.ConnectionFailed;
            if (n == 0) return error.ConnectionFailed;
            index += n;
        }
    }

    fn readReply(self: *Client, allocator: std.mem.Allocator) RedisError!Reply {
        const line = try self.readLine(allocator);
        defer allocator.free(line);
        if (line.len < 3) return error.ProtocolError;
        switch (line[0]) {
            '+' => return .{ .kind = .simple, .simple = try allocator.dupe(u8, line[1 .. line.len - 2]) },
            '-' => return .{ .kind = .err, .simple = try allocator.dupe(u8, line[1 .. line.len - 2]) },
            ':' => {
                const num = std.fmt.parseInt(i64, std.mem.trim(u8, line[1 .. line.len - 2], " \t"), 10) catch return error.ProtocolError;
                return .{ .kind = .integer, .integer = num };
            },
            '$' => {
                const len = std.fmt.parseInt(isize, std.mem.trim(u8, line[1 .. line.len - 2], " \t"), 10) catch return error.ProtocolError;
                if (len < 0) return .{ .kind = .null_bulk };
                const value = try allocator.alloc(u8, @intCast(len));
                errdefer allocator.free(value);
                try self.readFully(value);
                var crlf: [2]u8 = undefined;
                try self.readFully(&crlf);
                return .{ .kind = .bulk, .bulk = value };
            },
            else => return error.ProtocolError,
        }
    }
};

fn parsePortEnv(key: []const u8, default: u16) u16 {
    const raw = std.posix.getenv(key) orelse return default;
    const trimmed = std.mem.trim(u8, raw, " \t\r\n");
    if (trimmed.len == 0) return default;
    return std.fmt.parseInt(u16, trimmed, 10) catch default;
}

pub fn verifyStartup(client: *Client) void {
    const host = std.posix.getenv("REDIS_HOST") orelse "127.0.0.1";
    const port = parsePortEnv("REDIS_PORT", 6379);
    client.ping() catch |err| {
        std.log.warn("Redis startup verify failed host={s} port={d} error={s}", .{ host, port, @errorName(err) });
        return;
    };
    std.log.info("Redis startup verify succeeded host={s} port={d} pong=PONG", .{ host, port });
}
