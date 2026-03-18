-- Lua 脚本: 检查用户是否达到优惠券领取上限并记录领取次数

-- 参数列表：
-- KEYS[1]: 优惠券库存键 (coupon_stock_key)
-- KEYS[2]: 用户领取记录键 (user_coupon_key)
-- ARGV[1]: 优惠券有效期结束时间 (timestamp)
-- ARGV[2]: 用户领取上限 (limit)
local stock = tonumber(redis.call('HGET', KEYS[1], 'stock'))
if stock == nil or stock <= 0 then
    return {1, 0}
end

local userCouponCount = tonumber(redis.call('GET', KEYS[2]))
if userCouponCount == nil then
    userCouponCount = 0
end

local limit = tonumber(ARGV[2])
if userCouponCount >= limit then
    return {2, userCouponCount}
end

local newCount
if userCouponCount == 0 then
    redis.call('SET', KEYS[2], 1)
    newCount = 1
else
    newCount = redis.call('INCR', KEYS[2])
end

redis.call('EXPIRE', KEYS[2], tonumber(ARGV[1]))
redis.call('HINCRBY', KEYS[1], 'stock', -1)

return {0, tonumber(newCount)}