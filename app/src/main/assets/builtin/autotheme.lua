require "import"
import "android.os.Build"

local function autotheme()
  local version = Build.VERSION.SDK_INT
  local h = tonumber(os.date("%H"))
  if version >= 21 then
    if h <= 6 or h >= 22 then
      return android.R.style.Theme_Material
    else
      return android.R.style.Theme_Material_Light
    end
  else
    if h <= 6 or h >= 22 then
      return android.R.style.Theme_Holo
    else
      return android.R.style.Theme_Holo_Light
    end
  end
end

return autotheme
