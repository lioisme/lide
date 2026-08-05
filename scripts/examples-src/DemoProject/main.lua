-- =====================================
-- DemoProject: main.lua (entry page)
-- .alp multi-page + dex demo
-- =====================================

local calc = require("page_calc")
local info = require("page_info")

print("=== DemoProject ===")
print("This is a multi-page .alp demo.")
print("")
print("Available pages:")
print("  1. main  - this page")
print("  2. calc  - calculator page")
print("  3. info  - system info page")
print("")

-- demonstrate calling calc page
print("--- Calculator Demo ---")
local sum = calc.add(10, 25)
print("10 + 25 = " .. sum)
print("")

-- demonstrate calling info page
print("--- System Info Demo ---")
local sinfo = info.getInfo()
for k, v in pairs(sinfo) do
    print("  " .. k .. " = " .. tostring(v))
end
print("")
print("=== End of Demo ===")
