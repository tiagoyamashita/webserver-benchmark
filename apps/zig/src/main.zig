const std = @import("std");
const config_mod = @import("config.zig");
const db_mod = @import("db.zig");
const app_mod = @import("app.zig");
const server_mod = @import("server.zig");
const observability_log = @import("observability_log.zig");
const redis_mod = @import("redis_client.zig");
const session_mod = @import("auth/session.zig");

pub fn main() !void {
    var gpa = std.heap.GeneralPurposeAllocator(.{}){};
    defer _ = gpa.deinit();
    const allocator = gpa.allocator();

    var config = try config_mod.Config.fromEnv(allocator);
    observability_log.init(config.log_path) catch |err| {
        std.log.warn("observability log init failed: {}", .{err});
    };
    defer observability_log.deinit();

    const session_config = session_mod.SessionConfig.fromEnv(allocator);
    var app = app_mod.App{
        .allocator = allocator,
        .config = config,
        .db = null,
        .redis = null,
        .session_config = session_config,
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

    app.redis = redis_mod.Client.connectFromEnv(allocator) catch |err| blk: {
        std.log.warn("redis connect failed ({s}) — auth returns 503", .{@errorName(err)});
        break :blk null;
    };
    if (app.redis) |*client| {
        redis_mod.verifyStartup(client);
        std.log.info("connected to redis for session storage", .{});
    } else if (std.posix.getenv("REDIS_HOST") == null and std.posix.getenv("REDIS_URL") == null) {
        std.log.warn("REDIS_HOST/REDIS_URL not set — auth returns 503 until Redis is configured", .{});
    } else {
        std.log.warn("redis client not available — check REDIS_HOST/REDIS_URL and that redis is reachable", .{});
    }

    try server_mod.run(&app);
}
