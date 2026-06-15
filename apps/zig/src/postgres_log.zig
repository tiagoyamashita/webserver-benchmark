const std = @import("std");
const observability_log = @import("observability_log.zig");
const request_id_mod = @import("request_id.zig");
const snap = @import("request_snapshot.zig");

const SOURCE = "src/postgres_log.zig";
const SERVICE = "exercises-zig";

fn targetFields() [4]observability_log.Field {
    const host = std.posix.getenv("DB_HOST") orelse "";
    const port = std.posix.getenv("DB_PORT") orelse "5432";
    const dbname = std.posix.getenv("DB_NAME") orelse "demo";
    return .{
        .{ .string = .{ .key = "target_service", .value = "postgres" } },
        .{ .string = .{ .key = "host", .value = host } },
        .{ .string = .{ .key = "port", .value = port } },
        .{ .string = .{ .key = "dbname", .value = dbname } },
    };
}

fn resolveRequestId() []const u8 {
    if (snap.activeRequestId()) |id| return id;
    return "";
}

fn applicationName(buf: *[64]u8) []const u8 {
    const request_id = resolveRequestId();
    if (request_id.len == 0) return SERVICE;
    return request_id_mod.postgresApplicationName(buf, SERVICE, request_id);
}

pub fn logQuery(operation: []const u8, sql: []const u8) void {
    var app_name_buf: [64]u8 = undefined;
    const app_name = applicationName(&app_name_buf);
    const request_id = resolveRequestId();
    const target = targetFields();
    var extra: [8]observability_log.Field = undefined;
    var n: usize = 0;
    extra[n] = .{ .string = .{ .key = "operation", .value = operation } };
    n += 1;
    extra[n] = .{ .string = .{ .key = "sql", .value = sql } };
    n += 1;
    extra[n] = .{ .string = .{ .key = "application_name", .value = app_name } };
    n += 1;
    if (request_id.len > 0) {
        extra[n] = .{ .string = .{ .key = "request_id", .value = request_id } };
        n += 1;
    }
    for (target) |field| {
        extra[n] = field;
        n += 1;
    }
    observability_log.info("postgres_query", SOURCE, "postgres query", extra[0..n]);
}

pub fn logQueryFailed(operation: []const u8, sql: []const u8, err_msg: []const u8) void {
    var app_name_buf: [64]u8 = undefined;
    const app_name = applicationName(&app_name_buf);
    const request_id = resolveRequestId();
    const target = targetFields();
    var extra: [9]observability_log.Field = undefined;
    var n: usize = 0;
    extra[n] = .{ .string = .{ .key = "operation", .value = operation } };
    n += 1;
    extra[n] = .{ .string = .{ .key = "sql", .value = sql } };
    n += 1;
    extra[n] = .{ .string = .{ .key = "application_name", .value = app_name } };
    n += 1;
    extra[n] = .{ .string = .{ .key = "error", .value = err_msg } };
    n += 1;
    if (request_id.len > 0) {
        extra[n] = .{ .string = .{ .key = "request_id", .value = request_id } };
        n += 1;
    }
    for (target) |field| {
        extra[n] = field;
        n += 1;
    }
    observability_log.err("postgres_query", SOURCE, "postgres query failed", extra[0..n]);
}
