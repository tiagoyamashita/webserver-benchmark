const std = @import("std");
const http = @import("http_response.zig");
const request_id_mod = @import("request_id.zig");
const req_ctx = @import("request_context.zig");

const max_body_log_bytes: usize = 65_536;

const header_allow = [_][]const u8{
    "x-request-id",
    "x-request-origin",
    "x-dashboard-page",
    "x-session-id",
    "content-type",
    "accept",
    "user-agent",
    "host",
};

pub threadlocal var active: ?*Context = null;

pub const Context = struct {
    allocator: std.mem.Allocator,
    method: []const u8,
    path: []const u8,
    headers_json: []const u8,
    url_params_json: []const u8,
    body_bytes: ?[]const u8,
    body_json: []const u8,
    request_id: []const u8,
    start_ns: i128,
    response_status: std.http.Status,
    response_body_json: ?[]const u8,

    pub fn deinit(self: *Context) void {
        self.allocator.free(self.headers_json);
        self.allocator.free(self.url_params_json);
        self.allocator.free(self.body_json);
        self.allocator.free(self.request_id);
        if (self.body_bytes) |body| self.allocator.free(body);
        if (self.response_body_json) |body| self.allocator.free(body);
        self.allocator.destroy(self);
    }
};

pub fn begin(allocator: std.mem.Allocator, request: *std.http.Server.Request) !*Context {
    const path = http.targetOnly(http.pathname(request.head.target));
    const ctx = try allocator.create(Context);
    errdefer allocator.destroy(ctx);

    ctx.* = .{
        .allocator = allocator,
        .method = @tagName(request.head.method),
        .path = path,
        .headers_json = try headersJson(allocator, request),
        .url_params_json = try urlParamsJson(allocator, request.head.target),
        .body_bytes = null,
        .body_json = try allocator.dupe(u8, "{}"),
        .request_id = blk: {
            const header_id = try headerValue(allocator, request, "x-request-id");
            defer allocator.free(header_id);
            break :blk try request_id_mod.resolveInbound(allocator, header_id);
        },
        .start_ns = std.time.nanoTimestamp(),
        .response_status = .ok,
        .response_body_json = null,
    };

    if (shouldReadBody(request.head.method)) {
        const length = request.head.content_length orelse 0;
        if (length > 0) {
            const capped = @min(length, max_body_log_bytes);
            const body = try allocator.alloc(u8, capped);
            errdefer allocator.free(body);
            var reader = try request.reader();
            var read_total: usize = 0;
            while (read_total < capped) {
                const n = try reader.read(body[read_total..]);
                if (n == 0) break;
                read_total += n;
            }
            ctx.body_bytes = try allocator.dupe(u8, body[0..read_total]);
            allocator.free(body);
            const parsed = try bodyJson(allocator, ctx.body_bytes.?);
            allocator.free(ctx.body_json);
            ctx.body_json = parsed;
        }
    }

    active = ctx;
    req_ctx.setOrigin(ctx.method, ctx.path);
    req_ctx.setRequestId(ctx.request_id);
    return ctx;
}

pub fn end(ctx: *Context) void {
    if (active) |current| {
        if (current == ctx) active = null;
    }
    req_ctx.clearOrigin();
    ctx.deinit();
}

/// Resolved request id for the current thread (inbound snapshot or propagated context).
pub fn activeRequestId() ?[]const u8 {
    if (active) |ctx| {
        if (ctx.request_id.len > 0) return ctx.request_id;
    }
    return req_ctx.getRequestId();
}

pub fn preReadBody() ?[]const u8 {
    const ctx = active orelse return null;
    return ctx.body_bytes;
}

pub fn recordResponse(status: std.http.Status, body: []const u8, content_type_json: bool) void {
    const ctx = active orelse return;
    ctx.response_status = status;
    if (!content_type_json or body.len == 0) return;
    const capped = @min(body.len, 4096);
    const copy = ctx.allocator.alloc(u8, capped) catch return;
    @memcpy(copy, body[0..capped]);
    if (ctx.response_body_json) |existing| ctx.allocator.free(existing);
    ctx.response_body_json = copy;
}

pub fn elapsedMs(ctx: *const Context) u64 {
    const delta = std.time.nanoTimestamp() - ctx.start_ns;
    return @intCast(@max(@divTrunc(delta, std.time.ns_per_ms), 0));
}

fn shouldReadBody(method: std.http.Method) bool {
    return switch (method) {
        .POST, .PUT, .PATCH => true,
        else => false,
    };
}

fn headersJson(allocator: std.mem.Allocator, request: *std.http.Server.Request) ![]const u8 {
    var list = std.ArrayList(u8).init(allocator);
    errdefer list.deinit();
    try list.append('{');
    var first = true;
    var header_it = request.iterateHeaders();
    while (header_it.next()) |header| {
        const name = std.mem.trim(u8, header.name, " \t");
        if (!headerAllowed(name)) continue;
        if (!first) try list.append(',');
        first = false;
        var lower_buf: [128]u8 = undefined;
        const lowered = if (name.len <= lower_buf.len)
            std.ascii.lowerString(&lower_buf, name)
        else
            name;
        try appendJsonKeyValue(&list, lowered, header.value);
    }
    try list.append('}');
    return list.toOwnedSlice();
}

fn headerAllowed(name: []const u8) bool {
    for (header_allow) |allowed| {
        if (std.ascii.eqlIgnoreCase(name, allowed)) return true;
    }
    return false;
}

fn headerValue(allocator: std.mem.Allocator, request: *std.http.Server.Request, name: []const u8) ![]const u8 {
    var header_it = request.iterateHeaders();
    while (header_it.next()) |header| {
        if (std.ascii.eqlIgnoreCase(header.name, name)) {
            return allocator.dupe(u8, std.mem.trim(u8, header.value, " \t\r\n"));
        }
    }
    return allocator.dupe(u8, "");
}

fn urlParamsJson(allocator: std.mem.Allocator, target: []const u8) ![]const u8 {
    const q = std.mem.indexOfScalar(u8, target, '?') orelse return allocator.dupe(u8, "{}");
    const query = target[q + 1 ..];
    if (query.len == 0) return allocator.dupe(u8, "{}");

    var list = std.ArrayList(u8).init(allocator);
    errdefer list.deinit();
    try list.append('{');
    var first = true;
    var it = std.mem.splitScalar(u8, query, '&');
    while (it.next()) |pair| {
        if (pair.len == 0) continue;
        const eq = std.mem.indexOfScalar(u8, pair, '=') orelse continue;
        const key = pair[0..eq];
        const value = pair[eq + 1 ..];
        if (!first) try list.append(',');
        first = false;
        try appendJsonKeyValue(&list, key, value);
    }
    try list.append('}');
    return list.toOwnedSlice();
}

fn bodyJson(allocator: std.mem.Allocator, body: []const u8) ![]const u8 {
    const trimmed = std.mem.trim(u8, body, " \t\r\n");
    if (trimmed.len == 0) return allocator.dupe(u8, "{}");
    if (trimmed[0] == '{') {
        return allocator.dupe(u8, trimmed);
    }
    if (std.mem.indexOfScalar(u8, trimmed, '=') != null) {
        return formBodyJson(allocator, trimmed);
    }
  return try std.fmt.allocPrint(allocator, "{{\"_raw\":\"{s}\"}}", .{try jsonEscape(allocator, trimmed)});
}

fn formBodyJson(allocator: std.mem.Allocator, body: []const u8) ![]const u8 {
    var list = std.ArrayList(u8).init(allocator);
    errdefer list.deinit();
    try list.append('{');
    var first = true;
    var it = std.mem.splitScalar(u8, body, '&');
    while (it.next()) |pair| {
        if (pair.len == 0) continue;
        const eq = std.mem.indexOfScalar(u8, pair, '=') orelse continue;
        const key = pair[0..eq];
        const value = pair[eq + 1 ..];
        if (!first) try list.append(',');
        first = false;
        try appendJsonKeyValue(&list, key, value);
    }
    try list.append('}');
    return list.toOwnedSlice();
}

fn appendJsonKeyValue(list: *std.ArrayList(u8), key: []const u8, value: []const u8) !void {
    try list.append('"');
    try appendJsonEscaped(list, key);
    try list.appendSlice("\":\"");
    try appendJsonEscaped(list, value);
    try list.append('"');
}

fn appendJsonEscaped(list: *std.ArrayList(u8), value: []const u8) !void {
    for (value) |ch| {
        switch (ch) {
            '\\' => try list.appendSlice("\\\\"),
            '"' => try list.appendSlice("\\\""),
            '\n' => try list.appendSlice("\\n"),
            '\r' => try list.appendSlice("\\r"),
            '\t' => try list.appendSlice("\\t"),
            else => {
                if (ch < 0x20) {
                    try std.fmt.format(list.writer(), "\\u{:0>4}", .{ch});
                } else {
                    try list.append(ch);
                }
            },
        }
    }
}

fn jsonEscape(allocator: std.mem.Allocator, input: []const u8) ![]const u8 {
    var list = std.ArrayList(u8).init(allocator);
    errdefer list.deinit();
    try appendJsonEscaped(&list, input);
    return list.toOwnedSlice();
}
