require "import"
import "android.content.pm.PackageManager"

local pm = activity.getPackageManager()
local pkgInfo = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS)
local sharedPkgList = pkgInfo.requestedPermissions

permission = {}
permission_info = {}
permission_info["BIND_ACCESSIBILITY_SERVICE"] = "辅助功能"

if sharedPkgList then
  for i = 0, #sharedPkgList - 1 do
    local permName = sharedPkgList[i]
    local ok, tmpPermInfo = pcall(pm.getPermissionInfo, permName, 0)
    local shortName = permName:match("%.([%w_]+)$") or permName
    table.insert(permission, shortName)
    if ok then
      permission_info[shortName] = tmpPermInfo:loadLabel(pm)
    else
      permission_info[shortName] = shortName
    end
  end
end
