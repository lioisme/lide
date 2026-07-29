if type(_G.import) ~= "function" then
    error("import function not available - LuaRunner.setupCompat not called")
end

function dump(o)
    if type(o) == "table" then
        local s = "{ "
        for k, v in pairs(o) do
            if type(k) ~= "number" then k = "\"" .. k .. "\"" end
            s = s .. "[" .. k .. "] = " .. dump(v) .. ", "
        end
        return s .. "} "
    elseif type(o) == "string" then
        return "\"" .. o .. "\""
    else
        return tostring(o)
    end
end

function printstack()
    local level = 1
    while true do
        local info = debug.getinfo(level, "Sln")
        if not info then break end
        if info.what ~= "C" then
            print(string.format("stack[%d]: %s:%d - %s", level, info.short_src, info.currentline, info.name or ""))
        end
        level = level + 1
    end
end

function enum(o)
    local i = 0
    return function()
        i = i + 1
        local hasNext = o:hasNext()
        if hasNext then
            return o:next()
        end
    end
end

function each(o)
    if o.iterator then
        local iter = o:iterator()
        return function()
            if iter:hasNext() then
                return iter:next()
            end
        end
    end
    return function() end
end

return true
