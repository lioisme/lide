=== Dex Loading in .alp Projects ===

Place .dex or .jar files in this libs/ directory.

The delua app will load them via DexClassLoader
at runtime, making their Java classes available
to your Lua scripts through luajava bindings.

Example Lua code to load dex:
----------------------------------------
local ok = loadDex(projectDir .. "/libs/mylib.dex")
if ok then
    local cls = luajava.bindClass("com.example.MyClass")
    local obj = luajava.newInstance("com.example.MyClass")
    obj:myMethod()
end
----------------------------------------
