const std = @import("std");
const http = std.http;
const outbound_log = @import("outbound_http_log.zig");
const request_id_mod = @import("request_id.zig");
const req_ctx = @import("request_context.zig");
const snap = @import("request_snapshot.zig");

const max_response_log_bytes: usize = 4096;

pub const FetchResult = struct {
    status: u16,
    body: []const u8,
};

pub const FetchOptions = struct {
    payload: ?[]const u8 = null,
    content_type: ?[]const u8 = null,
    cookie: ?[]const u8 = null,
    /// Stack probes only need status; skip downloading large HTML landing pages.
    ignore_response_body: bool = false,
};

pub fn fetch(
    allocator: std.mem.Allocator,
    method: http.Method,
    url: []const u8,
    relay_target: []const u8,
    options: FetchOptions,
) !FetchResult {
    const method_name = @tagName(method);
    const inbound_id = currentInboundRequestId();
    const request_id = try request_id_mod.resolveOutbound(allocator, inbound_id);
    defer allocator.free(request_id);

    const request_body_json = try bodyJson(allocator, options.payload orelse "");
    defer allocator.free(request_body_json);
    const request_headers_json = try outboundHeadersJson(allocator, request_id, options.content_type);
    defer allocator.free(request_headers_json);

    outbound_log.logRequest(method_name, url, relay_target, request_id, request_headers_json, request_body_json);

    const start_ns = std.time.nanoTimestamp();
    var response_body = std.ArrayList(u8).init(allocator);
    errdefer response_body.deinit();

    var extra_headers_buf: [4]http.Header = undefined;
    var extra_count: usize = 2;
    extra_headers_buf[0] = .{ .name = "x-request-id", .value = request_id };
    extra_headers_buf[1] = .{ .name = "x-request-origin", .value = "exercises-zig" };
    if (options.content_type) |ct| {
        extra_headers_buf[extra_count] = .{ .name = "content-type", .value = ct };
        extra_count += 1;
    }
    if (options.cookie) |cookie| {
        extra_headers_buf[extra_count] = .{ .name = "cookie", .value = cookie };
        extra_count += 1;
    }

    var client = http.Client{ .allocator = allocator };
    defer client.deinit();

    const storage: http.Client.FetchOptions.ResponseStorage = if (options.ignore_response_body)
        .ignore
    else
        .{ .dynamic = &response_body };

    const fetch_result = client.fetch(.{
        .location = .{ .url = url },
        .method = method,
        .payload = options.payload,
        .extra_headers = extra_headers_buf[0..extra_count],
        .response_storage = storage,
        .max_append_size = if (options.ignore_response_body) null else max_response_log_bytes,
        .keep_alive = false,
    }) catch |err| {
        const ms = elapsedMs(start_ns);
        outbound_log.logFailure(method_name, url, relay_target, request_id, ms, @errorName(err));
        return err;
    };

    const status: u16 = @intFromEnum(fetch_result.status);
    const ms = elapsedMs(start_ns);
    const response_json = if (options.ignore_response_body)
        try allocator.dupe(u8, "{}")
    else
        try bodyJson(allocator, response_body.items);
    defer allocator.free(response_json);
    const ok = status >= 200 and status < 300;
    outbound_log.logResponse(method_name, url, relay_target, request_id, status, ms, response_json, ok);

    return .{
        .status = status,
        .body = if (options.ignore_response_body)
            try allocator.dupe(u8, "")
        else
            try response_body.toOwnedSlice(),
    };
}

pub fn get(allocator: std.mem.Allocator, url: []const u8, relay_target: []const u8) !FetchResult {
    return fetch(allocator, .GET, url, relay_target, .{ .ignore_response_body = true });
}

pub fn postJson(
    allocator: std.mem.Allocator,
    url: []const u8,
    relay_target: []const u8,
    payload: []const u8,
) !FetchResult {
    return fetch(allocator, .POST, url, relay_target, .{
        .payload = payload,
        .content_type = "application/json",
    });
}

fn elapsedMs(start_ns: i128) u64 {
    const delta = std.time.nanoTimestamp() - start_ns;
    return @intCast(@max(@divTrunc(delta, std.time.ns_per_ms), 0));
}

fn currentInboundRequestId() ?[]const u8 {
    if (snap.active) |ctx| {
        if (request_id_mod.isAcceptable(ctx.request_id)) return ctx.request_id;
    }
    if (req_ctx.getRequestId()) |id| {
        if (request_id_mod.isAcceptable(id)) return id;
    }
    return null;
}

fn outboundHeadersJson(allocator: std.mem.Allocator, request_id: []const u8, content_type: ?[]const u8) ![]const u8 {
    if (content_type) |ct| {
        return std.fmt.allocPrint(
            allocator,
            "{{\"x-request-id\":\"{s}\",\"x-request-origin\":\"exercises-zig\",\"content-type\":\"{s}\"}}",
            .{ request_id, ct },
        );
    }
    return std.fmt.allocPrint(
        allocator,
        "{{\"x-request-id\":\"{s}\",\"x-request-origin\":\"exercises-zig\"}}",
        .{request_id},
    );
}

fn bodyJson(allocator: std.mem.Allocator, body: []const u8) ![]const u8 {
    const trimmed = std.mem.trim(u8, body, " \t\r\n");
    if (trimmed.len == 0) return allocator.dupe(u8, "{}");
    if (trimmed[0] == '{') return allocator.dupe(u8, trimmed);
    if (trimmed[0] == '[') {
        return std.fmt.allocPrint(allocator, "{{\"_json\":{s}}}", .{trimmed});
    }
    return std.fmt.allocPrint(allocator, "{{\"_raw\":\"{s}\"}}", .{trimmed});
}
