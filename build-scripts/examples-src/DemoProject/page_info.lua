-- =====================================
-- page_info.lua - System Info page
-- .alp multi-page example
-- =====================================

local page = {}

function page.getInfo()
    return {
        appname = appname or "unknown",
        appver = appver or "?",
        packagename = packagename or "?",
        lua_version = _VERSION,
        platform = "Android"
    }
end

function page.getVersion()
    return _VERSION
end

function page.platform()
    return "LuaJ on Android (LIDE)"
end

return page
