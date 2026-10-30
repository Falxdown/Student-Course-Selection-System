-- KEYS[1] stock key, KEYS[2] selected-student set
-- ARGV[1] student id
-- return: 1 success, 0 sold out, 2 duplicate, -1 stock not initialized
local stockKey = KEYS[1]
local selectedKey = KEYS[2]
local studentId = ARGV[1]

if redis.call('SISMEMBER', selectedKey, studentId) == 1 then
    return 2
end

local raw = redis.call('GET', stockKey)
if not raw then
    return -1
end

local stock = tonumber(raw)
if stock == nil or stock <= 0 then
    return 0
end

redis.call('DECR', stockKey)
redis.call('SADD', selectedKey, studentId)
return 1
