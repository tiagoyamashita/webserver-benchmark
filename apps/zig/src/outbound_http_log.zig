const std = @import("std");
const obs = @import("observability_log.zig");
const snap = @import("request_snapshot.zig");
const req_ctx = @import("request_context.zig");

const LOGGER = "http.client";
const RELAY_ORIGIN = "exercises-zig";

pub fn logRequest(
    method: []const u8,
    url: []const u8,
    relay_target: []const u8,
    request_id: []const u8,
    headers_json: []const u8,
    body_json: []const u8,
) void {
    var msg_buf: [512]u8 = undefined;
    const origin = inboundOrigin();
    const message = std.fmt.bufPrint(
        &msg_buf,
        "{s} {s} outbound request request_id={s} origin={s} {s}",
        .{ method, pathFromUrl(url), request_id, origin.method orelse "", origin.path orelse "" },
    ) catch return;

    var fields_buf: [12]obs.Field = undefined;
    const fields = appendOriginFields(&fields_buf, &.{
        .{ .string = .{ .key = "method", .value = method } },
        .{ .string = .{ .key = "path", .value = pathFromUrl(url) } },
        .{ .string = .{ .key = "request_id", .value = request_id } },
        .{ .string = .{ .key = "phase", .value = "outbound_request" } },
        .{ .string = .{ .key = "relay_target", .value = relay_target } },
        .{ .string = .{ .key = "relay_origin", .value = RELAY_ORIGIN } },
        .{ .json = .{ .key = "headers", .value = headers_json } },
        .{ .json = .{ .key = "body", .value = body_json } },
    });
    obs.httpInfo(LOGGER, message, fields);
}

pub fn logResponse(
    method: []const u8,
    url: []const u8,
    relay_target: []const u8,
    request_id: []const u8,
    status: u16,
    ms: u64,
    response_body_json: []const u8,
    ok: bool,
) void {
    var status_buf: [8]u8 = undefined;
    const status_text = std.fmt.bufPrint(&status_buf, "{d}", .{status}) catch "0";
    var ms_buf: [24]u8 = undefined;
    const ms_text = std.fmt.bufPrint(&ms_buf, "{d}", .{ms}) catch "0";
    var msg_buf: [512]u8 = undefined;
    const message = if (ok)
        std.fmt.bufPrint(&msg_buf, "{s} {s} {s} outbound response request_id={s}", .{
            method,
            pathFromUrl(url),
            status_text,
            request_id,
        }) catch return
    else
        std.fmt.bufPrint(&msg_buf, "{s} {s} {s} outbound response request_id={s} error=HTTP {s}", .{
            method,
            pathFromUrl(url),
            status_text,
            request_id,
            status_text,
        }) catch return;

    var fields_buf: [12]obs.Field = undefined;
    const fields = appendOriginFields(&fields_buf, &.{
        .{ .string = .{ .key = "method", .value = method } },
        .{ .string = .{ .key = "path", .value = pathFromUrl(url) } },
        .{ .string = .{ .key = "request_id", .value = request_id } },
        .{ .string = .{ .key = "status", .value = status_text } },
        .{ .string = .{ .key = "ms", .value = ms_text } },
        .{ .string = .{ .key = "phase", .value = "outbound_response" } },
        .{ .string = .{ .key = "relay_target", .value = relay_target } },
        .{ .string = .{ .key = "relay_origin", .value = RELAY_ORIGIN } },
        .{ .json = .{ .key = "body", .value = response_body_json } },
    });
    if (ok) {
        obs.httpInfo(LOGGER, message, fields);
    } else {
        obs.httpWarn(LOGGER, message, fields);
    }
}

pub fn logFailure(
    method: []const u8,
    url: []const u8,
    relay_target: []const u8,
    request_id: []const u8,
    ms: u64,
    error_text: []const u8,
) void {
    var ms_buf: [24]u8 = undefined;
    const ms_text = std.fmt.bufPrint(&ms_buf, "{d}", .{ms}) catch "0";
    var msg_buf: [512]u8 = undefined;
    const message = std.fmt.bufPrint(&msg_buf, "{s} {s} outbound failed request_id={s}", .{
        method,
        pathFromUrl(url),
        request_id,
    }) catch return;

    var fields_buf: [12]obs.Field = undefined;
    const fields = appendOriginFields(&fields_buf, &.{
        .{ .string = .{ .key = "method", .value = method } },
        .{ .string = .{ .key = "path", .value = pathFromUrl(url) } },
        .{ .string = .{ .key = "request_id", .value = request_id } },
        .{ .string = .{ .key = "ms", .value = ms_text } },
        .{ .string = .{ .key = "phase", .value = "outbound_failed" } },
        .{ .string = .{ .key = "relay_target", .value = relay_target } },
        .{ .string = .{ .key = "relay_origin", .value = RELAY_ORIGIN } },
        .{ .string = .{ .key = "error", .value = error_text } },
    });
    obs.httpWarn(LOGGER, message, fields);
}

const InboundOrigin = struct {
    method: ?[]const u8 = null,
    path: ?[]const u8 = null,
};

fn inboundOrigin() InboundOrigin {
    if (snap.active) |ctx| {
        return .{ .method = ctx.method, .path = ctx.path };
    }
    const origin = req_ctx.getOrigin();
    return .{ .method = origin.method, .path = origin.path };
}

fn appendOriginFields(out: *[12]obs.Field, base: []const obs.Field) []const obs.Field {
    var count: usize = 0;
    for (base) |field| {
        out[count] = field;
        count += 1;
    }
    const origin = inboundOrigin();
    if (origin.method) |method| {
        out[count] = .{ .string = .{ .key = "origin_method", .value = method } };
        count += 1;
    }
    if (origin.path) |path| {
        out[count] = .{ .string = .{ .key = "origin_path", .value = path } };
        count += 1;
    }
    return out[0..count];
}

fn pathFromUrl(url: []const u8) []const u8 {
    const scheme = std.mem.indexOf(u8, url, "://") orelse return url;
    const after_scheme = url[scheme + 3 ..];
    const slash = std.mem.indexOfScalar(u8, after_scheme, '/') orelse return "/";
    return after_scheme[slash..];
}
