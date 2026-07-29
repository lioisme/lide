local hex = {}
setmetatable(hex, { __index = _G })
_ENV = hex

function dump(v, delimiter, stx, etx)
   local dump = ""
   if delimiter == nil then delimiter = "" end
   dump = v:gsub("(.)", function(c) return delimiter .. string.format("%02X", c:byte(1)) end)
   dump = dump:sub(1 + delimiter:len(), -1)
   if stx ~= nil then dump = stx .. dump end
   if etx ~= nil then dump = dump .. etx end
   return dump
end

function smart_dump(v)
   return v:gsub("%c+", function(raw) return dump(raw, "", "<", ">") end)
end

function pack(v)
   v = v:gsub("%s+", "")
   local pack = ""
   local table = {
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
       0,  1,  2,  3,  4,  5,  6,  7,  8,  9, -1, -1, -1, -1, -1, -1,
      -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
   }
   assert((v:len() % 2) == 0, "Length '" .. v .. "' (" .. v:len() .. ") length is odd")
   local first = -1
   for i = 1, v:len() do
      local c = v:byte(i)
      assert(table[c+1] > -1, "hex.pack(), non-hex char '" .. string.format("%c", c) .. "' at position " .. i)
      if first == -1 then
         first = table[c+1] * 16
      else
         pack = pack .. string.char(first + table[c+1])
         first = -1
      end
   end
   return pack
end

function smart_pack(v)
   return v:gsub("<(%x+)>", function(dump) return pack(dump) end)
end

return hex
