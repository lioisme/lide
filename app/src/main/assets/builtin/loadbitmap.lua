require "import"
import "android.graphics.BitmapFactory"
import "java.net.URL"
import "java.io.BufferedInputStream"
import "java.io.File"

local function loadbitmap(path)
  if path:find("^https?://") then
    local url = URL(path)
    local conn = url.openConnection()
    conn:setDoInput(true)
    conn:connect()
    local input = BufferedInputStream(conn:getInputStream())
    local bitmap = BitmapFactory.decodeStream(input)
    input:close()
    return bitmap
  end
  return BitmapFactory.decodeFile(path)
end

return loadbitmap
