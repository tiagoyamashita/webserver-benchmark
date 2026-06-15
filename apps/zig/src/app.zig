const std = @import("std");
const config_mod = @import("config.zig");
const db_mod = @import("db.zig");

pub const App = struct {
    allocator: std.mem.Allocator,
    config: config_mod.Config,
    db: ?db_mod.Db,
    db_mutex: std.Thread.Mutex = .{},

    pub fn deinit(self: *App) void {
        if (self.db) |*database| database.deinit();
        self.config.deinit(self.allocator);
    }
};
