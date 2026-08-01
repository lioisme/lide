
require "import"
import "android.app.*"
import "android.os.*"
import "android.widget.*"
import "android.view.*"
import "dkjson"

layout={
  LinearLayout;
  orientation="vertical";
  layout_height="fill";
  layout_width="fill";
  {
    ListView;
    id="list";
    layout_height="fill";
    layout_width="fill";
  };
};

activity.setContentView(loadlayout(layout))
item={
  LinearLayout;
  layout_width="fill";
  orientation="vertical";
  {
    TextView;
    layout_margin="5dp";
    textSize="17sp";
    textColor="0xFF000000";
    text="0xFF000000";
    id="content";
  };
};
adapter=LuaAdapter(activity,item)
list.setAdapter(adapter)

--13位毫秒级时间戳
function timestamp()
  import "java.util.Date"
  time=Date().getTime()
  return time
end

-- index随机数
function indexnum()
  math.randomseed(os.time())
  return math.floor(1e3 * math.random() + 1)
end

--base64编码User Agent
function base64encode(data)
  local Base64=luajava.bindClass("android.util.Base64")
  return Base64.encodeToString(String(data).getBytes(),Base64.NO_WRAP);
end

-- UUID生成
local function generateUUID()
  local function randomHex(length)
    local result = ""
    for i = 1, length do
      result = result .. string.format("%x", math.random(0, 15))
    end
    return result
  end

  local part1 = randomHex(13)
  local part2 = randomHex(14)
  local part3 = randomHex(8)
  local part4 = randomHex(6)
  local part5 = part1

  return string.format("%s-%s-%s-%s-%s", part1, part2, part3, part4, part5)
end

--md5加密
function md5(str)
  import "java.security.MessageDigest"
  local bytes = MessageDigest.getInstance("MD5").digest(String(str).getBytes("UTF-8"))
  local sb = StringBuilder()
  for i = 1, #bytes do
    local hex = Integer.toHexString(bytes[i] & 0xFF)
    if #hex == 1 then
      sb.append("0")
    end
    sb.append(hex)
  end
  return sb.toString()
end

--13位时间戳
time=timestamp()
ug="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36 Edg/137.0.0.0"
--base64编码user-agent
useragent=base64encode(ug)
--index随机数
index=indexnum()
treat="method=GET&timeStamp="..time.."&User-Agent="..useragent.."&index="..index.."&channelId=40009&sVersion=2&key=A013F70DB97834C0A5492378BD76C53A"
--随机uuid
uuid=generateUUID()
--md5加密
signkey=md5(treat)
url="https://piaofang.maoyan.com/dashboard-ajax/movie?orderType=0&uuid="..uuid.."&timeStamp="..time.."&User-Agent="..useragent.."&index="..index.."&channelId=40009&sVersion=2&signKey="..signkey.."&WuKongReady=h5"

header={
  ["Cookie"]="_lxsdk_cuid="..uuid.."; _lxsdk="..uuid.."; _lx_utm=utm_source%3Dgoogle%26utm_medium%3Dorganic; _lxsdk_s=197d60841aa-1ea-834-68b%7C%7C13",
  ["Host"]="piaofang.maoyan.com",
  ["Referer"]="https://piaofang.maoyan.com/dashboard/movie",
  ["Sec-Fetch-Dest"]="empty",
  ["Sec-Fetch-Mode"]="cors",
  ["Sec-Fetch-Site"]="same-origin",
  ["User-Agent"]=ug,
}
Http.get(url,header,function(code,body)

  json=dkjson.decode(body)

  list=json.movieList.list
  for i=1,#list do
    movieid=list[i].movieInfo.movieid--电影id
    moviename=list[i].movieInfo.movieName--电影名
    releaseinfo=list[i].movieInfo.releaseInfo--上映天数
    sumboxdesc=list[i].sumBoxDesc--综合票房
    box=list[i].boxRate--票房占比
    showcount=list[i].showCount--排片场次
    showcountrate=list[i].showCountRate--排片占比
    avgshow=list[i].avgShowView--场均人次
    avgseat=list[i].avgSeatView--上座率
    adapter.add{content="电影："..moviename.."\n上映天数："..releaseinfo.."\n综合票房："..sumboxdesc.."\n票房占比："..box.."\n排片场次："..string.format("%d",showcount).."\n排片占比："..showcountrate.."\n场均人次："..avgshow.."\n上座率："..avgseat}
  end


end)

