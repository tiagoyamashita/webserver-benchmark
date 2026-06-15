const std = @import("std");
const config_mod = @import("config.zig");
const db_mod = @import("db.zig");
const app_mod = @import("app.zig");
const server_mod = @import("server.zig");
const observability_log = @import("observability_log.zig");

pub fn main() !void {
    var gpa = std.heap.GeneralPurposeAllocator(.{}){};
    defer _ = gpa.deinit();
    const allocator = gpa.allocator();

    var config = try config_mod.Config.fromEnv(allocator);
    observability_log.init(config.log_path) catch |err| {
        std.log.warn("observability log init failed: {}", .{err});
    };
    defer observability_log.deinit();
    var app = app_mod.App{
        .allocator = allocator,
        .config = config,
        .db = null,
    };
    defer app.deinit();

    if (config.databaseConfigured()) {
        std.log.info(
            "connecting to postgres at {s}:{d} db={s} user={s}",
            .{ config.db_host.?, config.db_port, config.db_name, config.db_user },
        );
        const conninfo = try config.connectionString(allocator);
        defer allocator.free(conninfo);
        app.db = db_mod.Db.connect(allocator, conninfo) catch |err| blk: {
            std.log.warn("postgres unavailable ({s}) — /api/items returns 503", .{@errorName(err)});
            break :blk null;
        };
        if (app.db != null) {
            std.log.info("connected to postgres at {s}", .{config.db_host.?});
        }
    } else {
        std.log.warn("DB_HOST not set — /api/items returns 503 until Postgres is configured", .{});
    }

    try server_mod.run(&app);
}
