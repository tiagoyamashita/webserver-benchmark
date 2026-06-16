const std = @import("std");
const config_mod = @import("config.zig");
const db_mod = @import("db.zig");
const redis_mod = @import("redis_client.zig");
const session_mod = @import("auth/session.zig");

pub const App = struct {
    allocator: std.mem.Allocator,
    config: config_mod.Config,
    db: ?db_mod.Db,
    db_mutex: std.Thread.Mutex = .{},
    redis: ?redis_mod.Client,
    redis_mutex: std.Thread.Mutex = .{},
    session_config: session_mod.SessionConfig,

    pub fn deinit(self: *App) void {
        if (self.redis) |*client| client.deinit();
        self.session_config.deinit(self.allocator);
        if (self.db) |*database| database.deinit();
        self.config.deinit(self.allocator);
    }
};
