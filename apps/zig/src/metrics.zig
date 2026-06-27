const std = @import("std");

var request_total: u64 = 0;

pub fn incRequests() void {
    _ = @atomicRmw(u64, &request_total, .Add, 1, .monotonic);
}

pub fn render(allocator: std.mem.Allocator) ![]u8 {
    const value = @atomicLoad(u64, &request_total, .monotonic);
    return std.fmt.allocPrint(
        allocator,
        "# HELP webserver_benchmark_http_requests_total HTTP requests handled by the exercises Zig app\n" ++
            "# TYPE webserver_benchmark_http_requests_total counter\n" ++
            "webserver_benchmark_http_requests_total {d}\n",
        .{value},
    );
}
