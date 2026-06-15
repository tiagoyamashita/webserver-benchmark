const std = @import("std");
const net = std.net;
const app_mod = @import("app.zig");
const router = @import("router.zig");
const http = @import("http_response.zig");

pub const App = app_mod.App;

pub fn run(app: *App) !void {
    const address = try net.Address.parseIp4(app.config.host, app.config.port);
    var server = try address.listen(.{ .reuse_address = true });
    defer server.deinit();
    std.log.info("exercises-zig listening on {s}:{d}", .{ app.config.host, app.config.port });

    while (true) {
        const connection = try server.accept();
        const thread = try std.Thread.spawn(.{}, handleConnectionThread, .{ app, connection });
        thread.detach();
    }
}

fn handleConnectionThread(app: *App, connection: net.Server.Connection) void {
    handleConnection(app, connection) catch |err| {
        std.log.warn("connection error: {}", .{err});
    };
}

fn handleConnection(app: *App, connection: net.Server.Connection) !void {
    defer connection.stream.close();
    var read_buffer: [8192]u8 = undefined;
    var http_server = std.http.Server.init(connection, &read_buffer);

    while (http_server.state == .ready) {
        var request = http_server.receiveHead() catch |err| switch (err) {
            error.HttpConnectionClosing => return,
            else => return err,
        };
        router.recordRequest();
        router.handleRequest(app, &request) catch |err| {
            router.logRequestError(&request, err);
            http.writeTextResponse(&request, .internal_server_error, "text/plain", "internal server error") catch {};
        };
    }
}
