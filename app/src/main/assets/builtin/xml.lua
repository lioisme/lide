local xml = {}
setmetatable(xml, { __index = _G })
_ENV = xml

TAG = 0

local function xml_encode(s)
  s = string.gsub(s, "&", "&amp;")
  s = string.gsub(s, "<", "&lt;")
  s = string.gsub(s, ">", "&gt;")
  s = string.gsub(s, '"', "&quot;")
  s = string.gsub(s, "'", "&apos;")
  return s
end

function tag(var, t)
  if type(var) ~= "table" then return end
  if t == nil then return var[TAG] end
  var[TAG] = t
end

function new(arg)
  if type(arg) == "table" then
    setmetatable(arg, { __index = xml, __tostring = xml.str })
    return arg
  end
  local var = {}
  setmetatable(var, { __index = xml, __tostring = xml.str })
  if type(arg) == "string" then var[TAG] = arg end
  return var
end

function append(var, t)
  if type(var) ~= "table" then return end
  local newVar = new(t)
  var[#var + 1] = newVar
  return newVar
end

function str(var, indent, tagValue)
  if var == nil then return end
  indent = indent or 0
  local indentStr = string.rep("  ", indent)
  if type(var) == "table" then
    local tagName = var[0] or tagValue or type(var)
    local s = indentStr .. "<" .. tagName
    local children = ""
    for k, v in pairs(var) do
      if type(k) == "string" then
        if type(v) == "table" and k ~= "_M" then
          children = children .. str(v, indent + 1, k)
        else
          s = s .. " " .. k .. "=\"" .. xml_encode(tostring(v)) .. "\""
        end
      end
    end
    if #var == 0 and #children == 0 then
      s = s .. " />\n"
    elseif #var == 1 and type(var[1]) ~= "table" and #children == 0 then
      s = s .. ">" .. xml_encode(tostring(var[1])) .. "</" .. tagName .. ">\n"
    else
      s = s .. ">\n"
      for _, v in ipairs(var) do
        if type(v) == "string" then
          s = s .. indentStr .. "  " .. xml_encode(v) .. "\n"
        else
          s = s .. str(v, indent + 1)
        end
      end
      s = s .. children .. indentStr .. "</" .. tagName .. ">\n"
    end
    return s
  else
    local t = type(var)
    return indentStr .. "<" .. t .. "> " .. xml_encode(tostring(var)) .. " </" .. t .. ">\n"
  end
end

function save(var, filename)
  if not var then return end
  if not filename or #filename == 0 then return end
  local file = io.open(filename, "w")
  file:write('<?xml version="1.0"?>\n')
  file:write(str(var))
  io.close(file)
end

function find(var, tagName, attributeKey, attributeValue)
  if type(var) ~= "table" then return end
  if type(tagName) == "string" and #tagName == 0 then tagName = nil end
  if type(attributeKey) ~= "string" or #attributeKey == 0 then attributeKey = nil end
  if type(attributeValue) == "string" and #attributeValue == 0 then attributeValue = nil end
  if tagName ~= nil then
    if var[0] == tagName and (attributeValue == nil or var[attributeKey] == attributeValue) then
      setmetatable(var, { __index = xml, __tostring = xml.str })
      return var
    end
  else
    if attributeValue == nil or var[attributeKey] == attributeValue then
      setmetatable(var, { __index = xml, __tostring = xml.str })
      return var
    end
  end
  for _, v in ipairs(var) do
    if type(v) == "table" then
      local ret = find(v, tagName, attributeKey, attributeValue)
      if ret ~= nil then return ret end
    end
  end
end

return xml
