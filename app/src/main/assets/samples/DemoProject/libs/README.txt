=== Dex Loading in .alp Projects ===

Place .dex or .jar files in this libs/ directory.

The delua engine scans this directory at startup (LuaDexLoader.loadLibs)
and loads every .dex / .jar / .apk / .zip via DexClassLoader. Their Java
classes then become available to your Lua scripts through luajava,
using the standard "import" mechanism - no manual loading needed.

Example Lua code:
----------------------------------------
require "import"

-- Way 1 (recommended): import injects the whole package,
-- classes are then usable directly by simple name.
import "com.example.mylib.*"

-- Way 2: bind a single class explicitly.
local cls = luajava.bindClass("com.example.mylib.Helper")
local obj = cls()
obj:hello()

-- Way 3: dynamically load an extra dex at runtime
-- (loadDex is a luacontext method, available as "activity").
local ok, err = pcall(function()
    return activity.loadDex(projectDir .. "/libs/extra.dex")
end)
if not ok then
    print("loadDex failed: " .. tostring(err))
end
----------------------------------------

Note: dex files placed in libs/ are loaded automatically at engine
startup, so in most cases Way 1 is all you need. loadDex is only
required for dex files outside this directory.
