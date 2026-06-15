/// Inbound HTTP context for outbound correlation (valid for the current request thread).
pub threadlocal var origin_method: ?[]const u8 = null;
pub threadlocal var origin_path: ?[]const u8 = null;
pub threadlocal var origin_request_id: ?[]const u8 = null;

pub fn setOrigin(method: ?[]const u8, path: ?[]const u8) void {
    origin_method = method;
    origin_path = path;
}

pub fn setRequestId(request_id: ?[]const u8) void {
    origin_request_id = request_id;
}

pub fn clearOrigin() void {
    origin_method = null;
    origin_path = null;
    origin_request_id = null;
}

pub fn getOrigin() struct { method: ?[]const u8, path: ?[]const u8 } {
    return .{ .method = origin_method, .path = origin_path };
}

pub fn getRequestId() ?[]const u8 {
    return origin_request_id;
}
