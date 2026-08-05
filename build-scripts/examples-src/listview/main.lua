require "import"
import "android.app.*"
import "android.widget.*"
import "android.view.*"

layout = {
  LinearLayout,
  orientation = "vertical",
  layout_width = "fill",
  layout_height = "fill",
  {
    ListView,
    id = "list",
    layout_width = "fill",
    layout_height = "fill",
  },
}

activity.setContentView(loadlayout(layout))

item = {
  LinearLayout,
  orientation = "horizontal",
  layout_width = "fill",
  layout_height = "wrap",
  {
    TextView,
    id = "content",
    textSize = "17sp",
    textColor = "#FF333333",
    layout_width = "fill",
    layout_height = "wrap",
  },
}

data = {}
for i = 1, 20 do
  table.insert(data, { content = "Item " .. i })
end

adapter = LuaAdapter(activity, data, item)
list.setAdapter(adapter)
print("listview: adapter set, " .. #data .. " items")

list.setOnItemClickListener(AdapterView.OnItemClickListener{
  onItemClick = function(parent, view, position, id)
    local text = data[position + 1].content
    print("listview: clicked " .. tostring(text))
    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
  end,
})
