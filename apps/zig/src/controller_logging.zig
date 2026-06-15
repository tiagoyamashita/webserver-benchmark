const std = @import("std");
const obs = @import("observability_log.zig");
const snap = @import("request_snapshot.zig");

pub fn logReceived(handler: []const u8, source: []const u8, method: []const u8, path: []const u8) void {
    logReceivedFields(handler, source, method, path, &.{});
}

pub fn logReceivedFields(
    handler: []const u8,
    source: []const u8,
    method: []const u8,
    path: []const u8,
    extra: []const obs.Field,
) void {
    std.log.info("{s} request received source={s} method={s} path={s}", .{ handler, source, method, path });
    var fields_buf: [16]obs.Field = undefined;
    const merged = mergeHttpSnapshot(&fields_buf, extra);
    obs.info(handler, source, "request received", merged);
}

pub fn logSucceeded(handler: []const u8, source: []const u8, comptime detail_fmt: []const u8, detail_args: anytype) void {
    logSucceededFields(handler, source, detail_fmt, detail_args, &.{});
}

pub fn logSucceededFields(
    handler: []const u8,
    source: []const u8,
    comptime detail_fmt: []const u8,
    detail_args: anytype,
    extra: []const obs.Field,
) void {
    std.log.info("{s} succeeded source={s} " ++ detail_fmt, .{ handler, source } ++ detail_args);
    var msg_buf: [512]u8 = undefined;
    const message = if (comptime detail_fmt.len == 0)
        std.fmt.bufPrint(&msg_buf, "{s} succeeded", .{handler}) catch handler
    else
        std.fmt.bufPrint(&msg_buf, "{s} succeeded " ++ detail_fmt, .{handler} ++ detail_args) catch handler;
    var fields_buf: [16]obs.Field = undefined;
    const merged = mergeResponseBody(&fields_buf, extra);
    obs.info(handler, source, message, merged);
}

pub fn logWarn(handler: []const u8, source: []const u8, comptime detail_fmt: []const u8, detail_args: anytype) void {
    std.log.warn("{s} source={s} " ++ detail_fmt, .{ handler, source } ++ detail_args);
    var msg_buf: [512]u8 = undefined;
    const message = std.fmt.bufPrint(&msg_buf, "{s} " ++ detail_fmt, .{handler} ++ detail_args) catch handler;
    var fields_buf: [4]obs.Field = undefined;
    const fields = appendRequestId(&fields_buf, &. {});
    obs.warn(handler, source, message, fields);
}

pub fn logError(handler: []const u8, source: []const u8, comptime detail_fmt: []const u8, detail_args: anytype) void {
    std.log.err("{s} failed source={s} " ++ detail_fmt, .{ handler, source } ++ detail_args);
    var msg_buf: [512]u8 = undefined;
    const message = std.fmt.bufPrint(&msg_buf, "{s} failed " ++ detail_fmt, .{handler} ++ detail_args) catch handler;
    var fields_buf: [4]obs.Field = undefined;
    const fields = appendRequestId(&fields_buf, &. {});
    obs.err(handler, source, message, fields);
}

fn mergeHttpSnapshot(out: *[16]obs.Field, extra: []const obs.Field) []const obs.Field {
    var count: usize = 0;
    if (snap.activeRequestId()) |request_id| {
        out[count] = .{ .string = .{ .key = "request_id", .value = request_id } };
        count += 1;
    }
    if (snap.active) |ctx| {
        out[count] = .{ .string = .{ .key = "method", .value = ctx.method } };
        count += 1;
        out[count] = .{ .string = .{ .key = "path", .value = ctx.path } };
        count += 1;
        out[count] = .{ .json = .{ .key = "headers", .value = ctx.headers_json } };
        count += 1;
        out[count] = .{ .json = .{ .key = "url_params", .value = ctx.url_params_json } };
        count += 1;
        out[count] = .{ .json = .{ .key = "body", .value = ctx.body_json } };
        count += 1;
    }
    for (extra) |field| {
        if (count >= out.len) break;
        out[count] = field;
        count += 1;
    }
    return out[0..count];
}

fn mergeResponseBody(out: *[16]obs.Field, extra: []const obs.Field) []const obs.Field {
    var count: usize = 0;
    if (snap.activeRequestId()) |request_id| {
        out[count] = .{ .string = .{ .key = "request_id", .value = request_id } };
        count += 1;
    }
    if (snap.active) |ctx| {
        if (ctx.response_body_json) |body| {
            out[count] = .{ .json = .{ .key = "body", .value = body } };
            count += 1;
        }
    }
    for (extra) |field| {
        if (count >= out.len) break;
        out[count] = field;
        count += 1;
    }
    return out[0..count];
}

fn appendRequestId(out: *[4]obs.Field, base: []const obs.Field) []const obs.Field {
    var count: usize = 0;
    if (snap.activeRequestId()) |request_id| {
        out[count] = .{ .string = .{ .key = "request_id", .value = request_id } };
        count += 1;
    }
    for (base) |field| {
        if (count >= out.len) break;
        out[count] = field;
        count += 1;
    }
    return out[0..count];
}
