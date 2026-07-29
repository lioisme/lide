require "import"
import "java.lang.Runtime"
import "java.io.DataOutputStream"
import "java.io.DataInputStream"
import "java.io.InputStreamReader"
import "java.io.BufferedReader"

function su(...)
  local args = { ... }
  local process = Runtime.getRuntime().exec("su")
  local dos = DataOutputStream(process.getOutputStream())
  local dis = DataInputStream(process.getInputStream())
  for k, v in ipairs(args) do
    dos:writeBytes(v .. "\n")
    dos:flush()
  end
  dos:writeBytes("exit\n")
  dos:flush()
  process:waitFor()
  local reader = BufferedReader(InputStreamReader(dis))
  local lines = {}
  while true do
    local line = reader:readLine()
    if line == nil then break end
    table.insert(lines, line)
  end
  return table.concat(lines, "\n")
end

return su
