require "import"
import "android.app.*"
import "android.widget.*"
import "android.view.*"

layout = {
  LinearLayout,
  orientation = "vertical",
  gravity = "center",
  layout_width = "fill",
  layout_height = "fill",
  background = "#FF0099CC",
  {
    TextView,
    text = "Hello, delua!",
    textSize = "26sp",
    textColor = "#FFFFFFFF",
    layout_width = "wrap",
    layout_height = "wrap",
    layout_marginBottom = "24dp",
  },
  {
    Button,
    id = "btn",
    text = "Click Me",
    textSize = "18sp",
    layout_width = "wrap",
    layout_height = "wrap",
  },
}

activity.setContentView(loadlayout(layout))
print("hello: layout loaded")

btn.onClick = function()
  print("hello: button clicked")
  Toast.makeText(activity, "Hello from Lua!", Toast.LENGTH_SHORT).show()
end
