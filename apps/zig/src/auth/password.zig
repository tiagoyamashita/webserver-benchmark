const std = @import("std");

const c = @cImport({
    @cInclude("bcrypt.h");
});

pub fn hashPassword(allocator: std.mem.Allocator, raw: []const u8) ![]const u8 {
    if (raw.len == 0 or raw.len > 256) return error.HashFailed;
    var raw_buf: [257]u8 = undefined;
    @memcpy(raw_buf[0..raw.len], raw);
    raw_buf[raw.len] = 0;
    var salt: [c.BCRYPT_HASHSIZE]u8 = undefined;
    if (c.bcrypt_gensalt(12, &salt) != 0) return error.HashFailed;
    var hash: [c.BCRYPT_HASHSIZE]u8 = undefined;
    if (c.bcrypt_hashpw(&raw_buf, &salt, &hash) != 0) return error.HashFailed;
    const hash_len = std.mem.indexOfScalar(u8, &hash, 0) orelse hash.len;
    return try allocator.dupe(u8, hash[0..hash_len]);
}

pub fn verifyPassword(raw: []const u8, password_hash: []const u8) bool {
    if (raw.len == 0 or password_hash.len == 0 or raw.len > 256) return false;
    var raw_buf: [257]u8 = undefined;
    @memcpy(raw_buf[0..raw.len], raw);
    raw_buf[raw.len] = 0;
    var hash_buf: [c.BCRYPT_HASHSIZE]u8 = undefined;
    @memset(&hash_buf, 0);
    const copy_len = @min(password_hash.len, hash_buf.len - 1);
    @memcpy(hash_buf[0..copy_len], password_hash[0..copy_len]);
    return c.bcrypt_checkpw(&raw_buf, &hash_buf) == 0;
}

pub const HashFailed = error{HashFailed};
