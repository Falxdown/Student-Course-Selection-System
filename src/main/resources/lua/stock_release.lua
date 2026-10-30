-- KEYS[1] stock key, KEYS[2] selected-student set
-- ARGV[1] student id
-- return 1 if this call actually released a reservation, 0 otherwise
local stockKey = KEYS[1]
local selectedKey = KEYS[2]
local studentId = ARGV[1]

if redis.call('SREM', selectedKey, studentId) == 1 then
    redis.call('INCR', stockKey)
    return 1
end
return 0
