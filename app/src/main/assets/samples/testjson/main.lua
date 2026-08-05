--长按key复制路径
require "import"
import "android.app.*"
import "android.os.*"
import "android.widget.*"
import "android.graphics.*"
import "android.view.*"
import "dkjson"
import "android.content.Context"
import "android.content.ClipboardManager"
import "android.content.ClipData"

-- JSON 数据
local jsonStr = [[
{
  "code": 200,
  "req": "{\"aweme_id\":\"7615525123759607094\"}",
  "data": {
    "aweme_detail": {
      "is_top": 0,
      "aweme_id": "7615525123759607094",
      "create_time": 1773127640,
      "desc": "“游戏王不该被污蔑，反战作品却被拿来美化战争！” #充能计划 #青年创作者成长计划 #游戏王 #世界和平 #二次元",
      "statistics": {
        "play_count": 0,
        "digg_count": 133250,
        "share_count": 25804,
        "comment_count": 1632,
        "collect_count": 8175,
        "admire_count": 0,
        "aweme_id": "7615525123759607094"
      },
      "link_type": "douyin",
      "author": {
        "uid": "1950993595238186",
        "short_id": "90867229427",
        "unique_id": "90867229427",
        "nickname": "陨落的魂殿萧天尊",
        "signature": "桀桀桀~\n终于让我得到完整版焚诀了\n每天为大家分享国漫、日漫杂谈、动漫推荐\n让你最快学会动漫焚诀",
        "sec_uid": "MS4wLjABAAAA8gSfk-64z0jSFIfTN-zLaFVCdEhGp-m7BK75LoFLuVap5cnOCDZDPJgaRkB8T1xH",
        "avatar_thumb": {
          "height": 720,
          "uri": "100x100/aweme-avatar/tos-cn-i-c9aec8xkvj_b5bb78872e6d4d14ae8993ba490a72cc",
          "url_list": [
            "https://p3-pc.douyinpic.com/aweme/100x100/aweme-avatar/tos-cn-i-c9aec8xkvj_b5bb78872e6d4d14ae8993ba490a72cc.jpeg?from=327834062"
          ],
          "width": 720
        },
        "link": "https://www.douyin.com/user/MS4wLjABAAAA8gSfk-64z0jSFIfTN-zLaFVCdEhGp-m7BK75LoFLuVap5cnOCDZDPJgaRkB8T1xH"
      },
      "video": {
        "duration": 153434,
        "cover": {
          "height": 720,
          "uri": "tos-cn-i-dy/f5ca3781b88d4248b09dc0c12f3b75c0",
          "url_list": [
            "https://p3-pc-sign.douyinpic.com/obj/tos-cn-i-dy/f5ca3781b88d4248b09dc0c12f3b75c0?lk3s=138a59ce&x-expires=1774447200&x-signature=n6IZRWiedU9C9bmdwfK0BBz6MqE%3D&from=327834062_large&s=PackSourceEnum_AWEME_DETAIL&se=false&sc=dynamic_cover&biz_tag=pcweb_cover&l=2026031122200054888BD886C03C0761C0",
            "https://p9-pc-sign.douyinpic.com/obj/tos-cn-i-dy/f5ca3781b88d4248b09dc0c12f3b75c0?lk3s=138a59ce&x-expires=1774447200&x-signature=E7rm%2BDAlXpJdfCUK56EXARfX0F0%3D&from=327834062_large&s=PackSourceEnum_AWEME_DETAIL&se=false&sc=dynamic_cover&biz_tag=pcweb_cover&l=2026031122200054888BD886C03C0761C0"
          ],
          "width": 720
        },
        "bit_rate": [
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 1881904,
            "format": "mp4",
            "gear_name": "normal_1080_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 36093513,
              "file_cs": "c:0-124714-ca72|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "ec4db4bf323cfbb7e0caf25b08054455",
              "height": 1080,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_1080p_1881904",
              "url_list": [
                "https://v26-web.douyinvod.com/e463894cae088c46945e51f7b1576e58/69b1a4d9/video/tos/cn/tos-cn-ve-15/oE8AXrheuFR4ABD17p9Q9kgEZEQVfAADIv2cCl/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1837&bt=1837&cs=0&ds=4&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=aTdoZ2k4aDZoaWhkaWY1ZkBpMzNwbHg5cm52OTMzNGkzM0BiMGNiNTMzNl8xLzZgYF5iYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100B_100H_100K_100o_100w&dy_q=1773238800&feature_id=0ea98fd3bdc3c6c14a3d0804cc272721&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/ed8cf1604039be257a1d0b7561efab5b/69b1a4d9/video/tos/cn/tos-cn-ve-15/oE8AXrheuFR4ABD17p9Q9kgEZEQVfAADIv2cCl/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1837&bt=1837&cs=0&ds=4&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=aTdoZ2k4aDZoaWhkaWY1ZkBpMzNwbHg5cm52OTMzNGkzM0BiMGNiNTMzNl8xLzZgYF5iYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100o_100w_100B_100H_100K&dy_q=1773238800&feature_id=0ea98fd3bdc3c6c14a3d0804cc272721&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=eca2cccf9a2e4fbe936e658fedfe34ef&sign=ec4db4bf323cfbb7e0caf25b08054455&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1920
            },
            "quality_type": 1,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 408030}, {\\\"time\\\": 2, \\\"offset\\\": 727521}, {\\\"time\\\": 3, \\\"offset\\\": 792545}, {\\\"time\\\": 4, \\\"offset\\\": 952096}, {\\\"time\\\": 5, \\\"offset\\\": 1080428}, {\\\"time\\\": 10, \\\"offset\\\": 1728493}]\",\"format\":\"mp4\",\"definition\":\"1080p\",\"quality\":\"normal\",\"file_id\":\"eca2cccf9a2e4fbe936e658fedfe34ef\",\"applog_map\":{\"feature_id\":\"0ea98fd3bdc3c6c14a3d0804cc272721\"},\"manual_only\":1,\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 1818844,
            "format": "dash",
            "gear_name": "normal_1080_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 34883834,
              "file_hash": "8f626d28d8bf6b3505126dc24e015054",
              "height": 1080,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_1080p_1818844",
              "url_list": [
                "https://v26-web.douyinvod.com/4d98ea6d7339a5fec85a7a72d20102a7/69b2cc29/video/tos/cn/tos-cn-vd-0026/oUcTG2y2AAA7A7HeAiLwXsGGhevBi74IBBCKfA/media-video-avc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=1776&bt=1776&cs=0&ds=4&mime_type=video_mp4&qs=0&rc=ZjpnN2Y2OzhoaTNlM2RkNkBpMzNwbHg5cm52OTMzNGkzM0A2NDE2YmAxX2AxMGAzYGMzYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/bfd7f322dd636804b9c257e387d7f33c/69b2cc29/video/tos/cn/tos-cn-vd-0026/oUcTG2y2AAA7A7HeAiLwXsGGhevBi74IBBCKfA/media-video-avc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=1776&bt=1776&cs=0&ds=4&mime_type=video_mp4&qs=0&rc=ZjpnN2Y2OzhoaTNlM2RkNkBpMzNwbHg5cm52OTMzNGkzM0A2NDE2YmAxX2AxMGAzYGMzYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/dash/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=63faca60cb304fceb548c3ec944d779c&sign=8f626d28d8bf6b3505126dc24e015054&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1920
            },
            "quality_type": 1,
            "video_extra": "{\"PktOffsetMap\":\"\",\"format\":\"dash\",\"sub_info\":\"{\\\"audio_bitrate_target\\\":64,\\\"audio_channels\\\":\\\"2.0\\\",\\\"audio_layout\\\":\\\"C\\\",\\\"audio_profile\\\":\\\"aac_he_v2\\\",\\\"audio_sample_rate\\\":\\\"44100\\\",\\\"base_range_info\\\":{\\\"first_moof_range\\\":\\\"1329-3100\\\",\\\"index_range\\\":\\\"997-1328\\\",\\\"init_range\\\":\\\"0-996\\\"},\\\"check_info\\\":{\\\"check_info\\\":\\\"a:v0300fg10000d6nsck7og65urivmh0e0|b:0-1328-20ea53aad797dd8e1809a66675296132|c:0-1328-1264\\\"},\\\"first_segment_range\\\":\\\"1329-1046525\\\",\\\"gear_des_key\\\":\\\"0:DASH|1:normal|2:h264|4:1080p|5:normal|10000:105\\\",\\\"quality_type\\\":1}\",\"definition\":\"1080p\",\"quality\":\"normal\",\"quality_desc\":\"\",\"file_id\":\"63faca60cb304fceb548c3ec944d779c\",\"real_bitrate\":1881904,\"audio_file_id\":\"d5589af6765bd020b3a26b66454973db\",\"applog_map\":{\"feature_id\":\"0ea98fd3bdc3c6c14a3d0804cc272721\"},\"manual_only\":1,\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 1439718,
            "format": "mp4",
            "gear_name": "normal_540_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 27612715,
              "file_cs": "c:0-165954-1cf3|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "966ebcde73bc515861d16c16ac711de9",
              "height": 576,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_540p_1439718",
              "url_list": [
                "https://v26-web.douyinvod.com/3c41af46ebd04681b751fb2044f8e485/69b1a4d9/video/tos/cn/tos-cn-ve-15/o43p9dZ9aAVeQ9bdgIFcr2DDQfLgoO8EAABWvE/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1405&bt=1405&cs=0&ds=6&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=MzY4OmQ7OTs5Mzk5NWU4OkBpMzNwbHg5cm52OTMzNGkzM0BhYTYvMmAwNmMxMi81MjI0YSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100H_100K_100o_100w_100B&dy_q=1773238800&feature_id=f0150a16a324336cda5d6dd0b69ed299&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/876bb515011fac64f1b24f7bbba2c427/69b1a4d9/video/tos/cn/tos-cn-ve-15/o43p9dZ9aAVeQ9bdgIFcr2DDQfLgoO8EAABWvE/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1405&bt=1405&cs=0&ds=6&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=MzY4OmQ7OTs5Mzk5NWU4OkBpMzNwbHg5cm52OTMzNGkzM0BhYTYvMmAwNmMxMi81MjI0YSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=f0150a16a324336cda5d6dd0b69ed299&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=77555b82265640f69836c85a2795d3d0&sign=966ebcde73bc515861d16c16ac711de9&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1024
            },
            "quality_type": 20,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 375305}, {\\\"time\\\": 2, \\\"offset\\\": 652086}, {\\\"time\\\": 3, \\\"offset\\\": 704397}, {\\\"time\\\": 4, \\\"offset\\\": 820764}, {\\\"time\\\": 5, \\\"offset\\\": 906752}, {\\\"time\\\": 10, \\\"offset\\\": 1446705}]\",\"format\":\"mp4\",\"definition\":\"540p\",\"quality\":\"normal\",\"file_id\":\"77555b82265640f69836c85a2795d3d0\",\"applog_map\":{\"feature_id\":\"f0150a16a324336cda5d6dd0b69ed299\"},\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 1428066,
            "format": "mp4",
            "gear_name": "normal_720_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 27389250,
              "file_cs": "c:0-165905-7964|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "5b64f50e88b6be29939aa96a76bba19d",
              "height": 720,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_720p_1428066",
              "url_list": [
                "https://v26-web.douyinvod.com/7466ef1060c99da6617b90841662eb22/69b1a4d9/video/tos/cn/tos-cn-ve-15/137f2c9049844a628d37913bd21eccbb/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1394&bt=1394&cs=0&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=ZTlpODk4NDM1NjY4aTQ1ZEBpMzNwbHg5cm52OTMzNGkzM0A2LWMxMjA0NmIxYzAxX2MtYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=fea919893f650a8c49286568590446ef&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/62d844423f8fd732c209f6a42566985e/69b1a4d9/video/tos/cn/tos-cn-ve-15/137f2c9049844a628d37913bd21eccbb/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1394&bt=1394&cs=0&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=ZTlpODk4NDM1NjY4aTQ1ZEBpMzNwbHg5cm52OTMzNGkzM0A2LWMxMjA0NmIxYzAxX2MtYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100K_100o_100w_100B_100H&dy_q=1773238800&feature_id=fea919893f650a8c49286568590446ef&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=0af2b14f35f343e29071355645ff069b&sign=5b64f50e88b6be29939aa96a76bba19d&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1280
            },
            "quality_type": 10,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 359997}, {\\\"time\\\": 2, \\\"offset\\\": 618098}, {\\\"time\\\": 3, \\\"offset\\\": 674696}, {\\\"time\\\": 4, \\\"offset\\\": 804137}, {\\\"time\\\": 5, \\\"offset\\\": 903187}, {\\\"time\\\": 10, \\\"offset\\\": 1410785}]\",\"format\":\"mp4\",\"definition\":\"720p\",\"quality\":\"normal\",\"file_id\":\"0af2b14f35f343e29071355645ff069b\",\"applog_map\":{\"feature_id\":\"fea919893f650a8c49286568590446ef\"},\"manual_only\":1,\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 1359525,
            "format": "mp4",
            "gear_name": "low_720_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 26074678,
              "file_cs": "c:0-125824-ef6d|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "cb09103fa1986a632f1c2fdfa74b55f5",
              "height": 720,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_720p_1359525",
              "url_list": [
                "https://v26-web.douyinvod.com/32b5ac9d35352a832204ee0f741b46a6/69b1a4d9/video/tos/cn/tos-cn-ve-15/o0BwA1ArgFexuA8gQpED9tf4IuEAgZ7XADvQR9/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1327&bt=1327&cs=0&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=1&rc=NjM5Ojk6aTs0ZTtoaGVlM0BpMzNwbHg5cm52OTMzNGkzM0A1YDYwXy4tXmAxNGA2MTUwYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=0ea98fd3bdc3c6c14a3d0804cc272721&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/4bb8037b9c2209e8a5f27d32dbe8eec3/69b1a4d9/video/tos/cn/tos-cn-ve-15/o0BwA1ArgFexuA8gQpED9tf4IuEAgZ7XADvQR9/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1327&bt=1327&cs=0&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=1&rc=NjM5Ojk6aTs0ZTtoaGVlM0BpMzNwbHg5cm52OTMzNGkzM0A1YDYwXy4tXmAxNGA2MTUwYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100K_100o_100w_100B_100H&dy_q=1773238800&feature_id=0ea98fd3bdc3c6c14a3d0804cc272721&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=308b4b9ece784bc1a80f17b63796c083&sign=cb09103fa1986a632f1c2fdfa74b55f5&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1280
            },
            "quality_type": 211,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 321522}, {\\\"time\\\": 2, \\\"offset\\\": 548931}, {\\\"time\\\": 3, \\\"offset\\\": 598585}, {\\\"time\\\": 4, \\\"offset\\\": 722635}, {\\\"time\\\": 5, \\\"offset\\\": 817118}, {\\\"time\\\": 10, \\\"offset\\\": 1277236}]\",\"format\":\"mp4\",\"definition\":\"720p\",\"quality\":\"low\",\"file_id\":\"308b4b9ece784bc1a80f17b63796c083\",\"applog_map\":{\"feature_id\":\"0ea98fd3bdc3c6c14a3d0804cc272721\"},\"manual_only\":1,\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 1296417,
            "format": "dash",
            "gear_name": "low_720_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 24864145,
              "file_hash": "f39119eeff5f3f30b5cd3d6c09d16ff5",
              "height": 720,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_720p_1296417",
              "url_list": [
                "https://v26-web.douyinvod.com/81836b81fbec6dcd93cb613de78c7f78/69b2cc29/video/tos/cn/tos-cn-vd-0026/oI9CnAQ9AeAggAupc8DFEZorIBLfTAMvOgD1A3/media-video-avc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=1266&bt=1266&cs=0&ds=3&mime_type=video_mp4&qs=1&rc=ZGY2OmVoaTU3PDo2aGU6NkBpMzNwbHg5cm52OTMzNGkzM0AxMDQwNF81NjIxYV4xYGNjYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100o_100w&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/ce1620bd795ab3f4bd3c23b1b7d7e37b/69b2cc29/video/tos/cn/tos-cn-vd-0026/oI9CnAQ9AeAggAupc8DFEZorIBLfTAMvOgD1A3/media-video-avc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=1266&bt=1266&cs=0&ds=3&mime_type=video_mp4&qs=1&rc=ZGY2OmVoaTU3PDo2aGU6NkBpMzNwbHg5cm52OTMzNGkzM0AxMDQwNF81NjIxYV4xYGNjYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/dash/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=f3f7cb4ea3d74954928fbe7b37733c4a&sign=f39119eeff5f3f30b5cd3d6c09d16ff5&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1280
            },
            "quality_type": 211,
            "video_extra": "{\"PktOffsetMap\":\"\",\"format\":\"dash\",\"sub_info\":\"{\\\"audio_bitrate_target\\\":64,\\\"audio_channels\\\":\\\"2.0\\\",\\\"audio_layout\\\":\\\"C\\\",\\\"audio_profile\\\":\\\"aac_he_v2\\\",\\\"audio_sample_rate\\\":\\\"44100\\\",\\\"base_range_info\\\":{\\\"first_moof_range\\\":\\\"1327-3098\\\",\\\"index_range\\\":\\\"995-1326\\\",\\\"init_range\\\":\\\"0-994\\\"},\\\"check_info\\\":{\\\"check_info\\\":\\\"a:v0300fg10000d6nsck7og65urivmh0e0|b:0-1326-9e5ca989ce592870041edad5d0cb95c3|c:0-1326-77af\\\"},\\\"first_segment_range\\\":\\\"1327-745656\\\",\\\"gear_des_key\\\":\\\"0:DASH|1:normal|2:h264|4:720p|5:low|10000:105\\\",\\\"quality_type\\\":11}\",\"definition\":\"720p\",\"quality\":\"low\",\"quality_desc\":\"\",\"file_id\":\"f3f7cb4ea3d74954928fbe7b37733c4a\",\"real_bitrate\":1359525,\"audio_file_id\":\"d5589af6765bd020b3a26b66454973db\",\"applog_map\":{\"feature_id\":\"0ea98fd3bdc3c6c14a3d0804cc272721\"},\"manual_only\":1,\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 1249774,
            "format": "mp4",
            "gear_name": "low_540_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 23969740,
              "file_cs": "c:0-125936-c81a|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "0ec6e5155576d49e7d980f8c6c80f72b",
              "height": 576,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_540p_1249774",
              "url_list": [
                "https://v26-web.douyinvod.com/129f3b1a06c93c44191ecaf212621704/69b1a4d9/video/tos/cn/tos-cn-ve-15/o0BfhcyIbv72BRcgICLTAf6GAwGzbBVqe4XBi7/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1220&bt=1220&cs=0&ds=6&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=1&rc=OzUzMzU8PDo4ZDtlaWk4O0BpMzNwbHg5cm52OTMzNGkzM0BhM2BhLTZhNjUxLTFfXjVhYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100B_100H_100K_100o_100w&dy_q=1773238800&feature_id=37f92ebd2877ae8e7eba995d406c5150&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/6b828d028e0ebec59f9b5c5514941ea7/69b1a4d9/video/tos/cn/tos-cn-ve-15/o0BfhcyIbv72BRcgICLTAf6GAwGzbBVqe4XBi7/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1220&bt=1220&cs=0&ds=6&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=1&rc=OzUzMzU8PDo4ZDtlaWk4O0BpMzNwbHg5cm52OTMzNGkzM0BhM2BhLTZhNjUxLTFfXjVhYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=37f92ebd2877ae8e7eba995d406c5150&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=d885afbf4b084a8597d99902d0c062d8&sign=0ec6e5155576d49e7d980f8c6c80f72b&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1024
            },
            "quality_type": 292,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 307860}, {\\\"time\\\": 2, \\\"offset\\\": 520367}, {\\\"time\\\": 3, \\\"offset\\\": 565798}, {\\\"time\\\": 4, \\\"offset\\\": 687689}, {\\\"time\\\": 5, \\\"offset\\\": 770528}, {\\\"time\\\": 10, \\\"offset\\\": 1192739}]\",\"format\":\"mp4\",\"definition\":\"540p\",\"quality\":\"low\",\"file_id\":\"d885afbf4b084a8597d99902d0c062d8\",\"applog_map\":{\"feature_id\":\"37f92ebd2877ae8e7eba995d406c5150\"},\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 1186646,
            "format": "dash",
            "gear_name": "low_540_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 22758837,
              "file_hash": "30fdbf9545c8179aad7c80fab73870c2",
              "height": 576,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_540p_1186646",
              "url_list": [
                "https://v26-web.douyinvod.com/27bfd23c7b620cad5c447d27f72e0b8e/69b2cc29/video/tos/cn/tos-cn-vd-0026/o4Ae4Ly3Ari7jG7BAwfBhcA2K5IBXTDGAACfiv/media-video-avc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=1158&bt=1158&cs=0&ds=6&mime_type=video_mp4&qs=1&rc=O2U0ZGQ0aDw6NmgzNjZkaUBpMzNwbHg5cm52OTMzNGkzM0BeLjYzMF5gNTMxLjYwLWNiYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/7fc2a58da4e623c36112eeeec4c1472d/69b2cc29/video/tos/cn/tos-cn-vd-0026/o4Ae4Ly3Ari7jG7BAwfBhcA2K5IBXTDGAACfiv/media-video-avc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=1158&bt=1158&cs=0&ds=6&mime_type=video_mp4&qs=1&rc=O2U0ZGQ0aDw6NmgzNjZkaUBpMzNwbHg5cm52OTMzNGkzM0BeLjYzMF5gNTMxLjYwLWNiYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/dash/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=effa0333901e436789cea13a6a911ba8&sign=30fdbf9545c8179aad7c80fab73870c2&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1024
            },
            "quality_type": 292,
            "video_extra": "{\"PktOffsetMap\":\"\",\"format\":\"dash\",\"sub_info\":\"{\\\"audio_bitrate_target\\\":64,\\\"audio_channels\\\":\\\"2.0\\\",\\\"audio_layout\\\":\\\"C\\\",\\\"audio_profile\\\":\\\"aac_he_v2\\\",\\\"audio_sample_rate\\\":\\\"44100\\\",\\\"base_range_info\\\":{\\\"first_moof_range\\\":\\\"1327-3098\\\",\\\"index_range\\\":\\\"995-1326\\\",\\\"init_range\\\":\\\"0-994\\\"},\\\"check_info\\\":{\\\"check_info\\\":\\\"a:v0300fg10000d6nsck7og65urivmh0e0|b:0-1326-97cb528afdd26627e7d84fee197567f0|c:0-1326-a464\\\"},\\\"first_segment_range\\\":\\\"1327-691399\\\",\\\"gear_des_key\\\":\\\"0:DASH|1:normal|2:h264|4:540p|5:low|10000:105\\\"}\",\"definition\":\"540p\",\"quality\":\"low\",\"quality_desc\":\"\",\"file_id\":\"effa0333901e436789cea13a6a911ba8\",\"real_bitrate\":1249774,\"audio_file_id\":\"d5589af6765bd020b3a26b66454973db\",\"applog_map\":{\"feature_id\":\"37f92ebd2877ae8e7eba995d406c5150\"},\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 1047430,
            "format": "mp4",
            "gear_name": "lower_540_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 20088925,
              "file_cs": "c:0-125436-8c43|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "0976c7e69f6057ec2c3617d7e3fa07ba",
              "height": 576,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_540p_1047430",
              "url_list": [
                "https://v26-web.douyinvod.com/1d9d4d57794d45be286923e5c23934fe/69b1a4d9/video/tos/cn/tos-cn-ve-15/ooKrDIQED9mZ8B0ggEfgASQKoF3LTpAAtec9Hv/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1022&bt=1022&cs=0&ds=6&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=4&rc=MztoN2kzaWYzOzdkZWU0NEBpMzNwbHg5cm52OTMzNGkzM0AyNS8zMzAuXjAxNWAxYGJgYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=eb29b1b3aca69db49524c333df8caaf7&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/e9dd6b02f6cda88f1e63feb0ac09de2b/69b1a4d9/video/tos/cn/tos-cn-ve-15/ooKrDIQED9mZ8B0ggEfgASQKoF3LTpAAtec9Hv/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1022&bt=1022&cs=0&ds=6&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=4&rc=MztoN2kzaWYzOzdkZWU0NEBpMzNwbHg5cm52OTMzNGkzM0AyNS8zMzAuXjAxNWAxYGJgYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=eb29b1b3aca69db49524c333df8caaf7&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=c1e1cb4bca844830ac1f306f642e8850&sign=0976c7e69f6057ec2c3617d7e3fa07ba&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1024
            },
            "quality_type": 224,
            "video_extra": "{\"PktOffsetMap\":\"\",\"format\":\"mp4\",\"definition\":\"540p\",\"quality\":\"lower\",\"file_id\":\"c1e1cb4bca844830ac1f306f642e8850\",\"applog_map\":{\"feature_id\":\"eb29b1b3aca69db49524c333df8caaf7\"},\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 921970,
            "format": "mp4",
            "gear_name": "adapt_low_540_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 17682704,
              "file_cs": "c:0-125983-0744|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "69e751540f6b2d670f5cf500a6d91c92",
              "height": 576,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_540p_921970",
              "url_list": [
                "https://v26-web.douyinvod.com/2e5067bb071384d4482e53e168ccaaa9/69b1a4d9/video/tos/cn/tos-cn-ve-15/og4mCE2VDeYvtfjgBfvlDA6XChGfiAEtxDCMyA/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=900&bt=900&cs=0&ds=6&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=12&rc=Ozs3NjdpNTM8aDVlZThnZUBpMzNwbHg5cm52OTMzNGkzM0A0YTAuYTM1NV4xYl8uYF4uYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=37f92ebd2877ae8e7eba995d406c5150&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/346ef7e2b4ba11aad0c4737edc0743d6/69b1a4d9/video/tos/cn/tos-cn-ve-15/og4mCE2VDeYvtfjgBfvlDA6XChGfiAEtxDCMyA/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=900&bt=900&cs=0&ds=6&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=12&rc=Ozs3NjdpNTM8aDVlZThnZUBpMzNwbHg5cm52OTMzNGkzM0A0YTAuYTM1NV4xYl8uYF4uYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=37f92ebd2877ae8e7eba995d406c5150&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=1badc51bbbe24ea980826fd41334d878&sign=69e751540f6b2d670f5cf500a6d91c92&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1024
            },
            "quality_type": 291,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 258938}, {\\\"time\\\": 2, \\\"offset\\\": 414387}, {\\\"time\\\": 3, \\\"offset\\\": 452100}, {\\\"time\\\": 4, \\\"offset\\\": 549139}, {\\\"time\\\": 5, \\\"offset\\\": 608299}, {\\\"time\\\": 10, \\\"offset\\\": 916821}]\",\"format\":\"mp4\",\"definition\":\"540p\",\"quality\":\"adapt_low\",\"file_id\":\"1badc51bbbe24ea980826fd41334d878\",\"applog_map\":{\"feature_id\":\"37f92ebd2877ae8e7eba995d406c5150\"},\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 858838,
            "format": "dash",
            "gear_name": "adapt_low_540_0",
            "is_bytevc1": 0,
            "is_h265": 0,
            "play_addr": {
              "data_size": 16471753,
              "file_hash": "d0e4ae28083147ea8417e07ff1e0e35d",
              "height": 576,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_h264_540p_858838",
              "url_list": [
                "https://v26-web.douyinvod.com/86418725930ede0d82f0c31e3b27cb66/69b2cc29/video/tos/cn/tos-cn-vd-0026/o4XAwCI4sHGA7nABycAiL3fBe7GAKTABf2ihvY/media-video-avc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=838&bt=838&cs=0&ds=6&mime_type=video_mp4&qs=12&rc=NDNpNGY6OzM4OjhmaDNlOEBpMzNwbHg5cm52OTMzNGkzM0BfL2JiNF4zNl4xXzI0LTEtYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/214f8cd1299c37cd763d0d2e4865d549/69b2cc29/video/tos/cn/tos-cn-vd-0026/o4XAwCI4sHGA7nABycAiL3fBe7GAKTABf2ihvY/media-video-avc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=838&bt=838&cs=0&ds=6&mime_type=video_mp4&qs=12&rc=NDNpNGY6OzM4OjhmaDNlOEBpMzNwbHg5cm52OTMzNGkzM0BfL2JiNF4zNl4xXzI0LTEtYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/dash/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=054b007e5cb547a59068a77ce1ef20b1&sign=d0e4ae28083147ea8417e07ff1e0e35d&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1024
            },
            "quality_type": 291,
            "video_extra": "{\"PktOffsetMap\":\"\",\"format\":\"dash\",\"sub_info\":\"{\\\"audio_bitrate_target\\\":64,\\\"audio_channels\\\":\\\"2.0\\\",\\\"audio_layout\\\":\\\"C\\\",\\\"audio_profile\\\":\\\"aac_he_v2\\\",\\\"audio_sample_rate\\\":\\\"44100\\\",\\\"base_range_info\\\":{\\\"first_moof_range\\\":\\\"1326-3097\\\",\\\"index_range\\\":\\\"994-1325\\\",\\\"init_range\\\":\\\"0-993\\\"},\\\"check_info\\\":{\\\"check_info\\\":\\\"a:v0300fg10000d6nsck7og65urivmh0e0|b:0-1325-3d4f479e72aae98438f090618d9e61c6|c:0-1325-5b25\\\"},\\\"first_segment_range\\\":\\\"1326-508532\\\",\\\"gear_des_key\\\":\\\"0:DASH|1:normal|2:h264|4:540p|5:adapt_low|10000:105\\\",\\\"quality_type\\\":291}\",\"definition\":\"540p\",\"quality\":\"adapt_low\",\"quality_desc\":\"\",\"file_id\":\"054b007e5cb547a59068a77ce1ef20b1\",\"real_bitrate\":921970,\"audio_file_id\":\"d5589af6765bd020b3a26b66454973db\",\"applog_map\":{\"feature_id\":\"37f92ebd2877ae8e7eba995d406c5150\"},\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 60,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 835944,
            "format": "mp4",
            "gear_name": "adapt_lowest_1080_1",
            "is_bytevc1": 1,
            "is_h265": 1,
            "play_addr": {
              "data_size": 16030809,
              "file_cs": "c:0-168963-ff82|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "76106dc67f349aa6dadc3c3c01abca1c",
              "height": 1080,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_bytevc1_1080p_835944",
              "url_list": [
                "https://v26-web.douyinvod.com/aa3c794bc97bdad79e84057c400f518e/69b1a4d9/video/tos/cn/tos-cn-ve-15/ogGvJI7AyRsXhLBBBGYe62orei9wC7AcfTg4AB/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=816&bt=816&cs=2&ds=4&eid=45312&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=15&rc=O2c5NTloaGkzZzRoZjkzNkBpMzNwbHg5cm52OTMzNGkzM0BeYC01My5gNi0xNjQvMjFgYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=757dd2cf707fffcef8d1ad2a3dd536ea&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/533a40816b0604a5eeb466c0a58b4ce4/69b1a4d9/video/tos/cn/tos-cn-ve-15/ogGvJI7AyRsXhLBBBGYe62orei9wC7AcfTg4AB/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=816&bt=816&cs=2&ds=4&eid=45312&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=15&rc=O2c5NTloaGkzZzRoZjkzNkBpMzNwbHg5cm52OTMzNGkzM0BeYC01My5gNi0xNjQvMjFgYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100K_100o_100w_100B_100H&dy_q=1773238800&feature_id=757dd2cf707fffcef8d1ad2a3dd536ea&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=c340562c7e914d009fce1e668206cda8&sign=76106dc67f349aa6dadc3c3c01abca1c&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1920
            },
            "quality_type": 2,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 303959}, {\\\"time\\\": 2, \\\"offset\\\": 433129}, {\\\"time\\\": 3, \\\"offset\\\": 479266}, {\\\"time\\\": 4, \\\"offset\\\": 568376}, {\\\"time\\\": 5, \\\"offset\\\": 617075}, {\\\"time\\\": 10, \\\"offset\\\": 952776}]\",\"format\":\"mp4\",\"definition\":\"1080p\",\"quality\":\"adapt_lowest\",\"file_id\":\"c340562c7e914d009fce1e668206cda8\",\"applog_map\":{\"feature_id\":\"757dd2cf707fffcef8d1ad2a3dd536ea\"},\"manual_only\":1,\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 622336,
            "format": "mp4",
            "gear_name": "720_2_1",
            "is_bytevc1": 1,
            "is_h265": 1,
            "play_addr": {
              "data_size": 11935945,
              "file_cs": "c:0-127958-5e59|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "d259d2b84f0176580ab4ba5862132e9b",
              "height": 720,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_bytevc1_720p_622336",
              "url_list": [
                "https://v26-web.douyinvod.com/e292d7effc071cd94045383bd102ed4d/69b1a4d9/video/tos/cn/tos-cn-ve-15/owexmCitlBYEAtADAJVDEtffhMvC2vDgTbfy4E/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=607&bt=607&cs=2&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=aDhpNGg3Zmk6PGQ5OmllNEBpMzNwbHg5cm52OTMzNGkzM0A1MGBhYTEvXl4xLWEtXi5hYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100o_100w_100B_100H_100K&dy_q=1773238800&feature_id=df3fbde7708fa2439dee16087ff4afbc&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/8e345d103c3b7cf2106c030172ce1768/69b1a4d9/video/tos/cn/tos-cn-ve-15/owexmCitlBYEAtADAJVDEtffhMvC2vDgTbfy4E/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=607&bt=607&cs=2&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=aDhpNGg3Zmk6PGQ5OmllNEBpMzNwbHg5cm52OTMzNGkzM0A1MGBhYTEvXl4xLWEtXi5hYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100H_100K_100o_100w_100B&dy_q=1773238800&feature_id=df3fbde7708fa2439dee16087ff4afbc&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=d11baf07d60a49a7af2c44ded1cf358e&sign=d259d2b84f0176580ab4ba5862132e9b&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1280
            },
            "quality_type": 12,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 223936}, {\\\"time\\\": 2, \\\"offset\\\": 315361}, {\\\"time\\\": 3, \\\"offset\\\": 349171}, {\\\"time\\\": 4, \\\"offset\\\": 421885}, {\\\"time\\\": 5, \\\"offset\\\": 463157}, {\\\"time\\\": 10, \\\"offset\\\": 706879}]\",\"format\":\"mp4\",\"definition\":\"720p\",\"quality\":\"normal\",\"file_id\":\"d11baf07d60a49a7af2c44ded1cf358e\",\"v_gear_id\":\"aweme/high_value_group-v3_0_r2\",\"u_vmaf\":94.3164,\"applog_map\":{\"feature_id\":\"df3fbde7708fa2439dee16087ff4afbc\"},\"sr_sharpness_strength\":8,\"mvmaf\":\"{\\\"mvmaf_sr_v1080\\\":96.021,\\\"mvmaf_sr_v960\\\":97.441,\\\"mvmaf_sr_v864\\\":98.063,\\\"mvmaf_sr_v720\\\":98.777,\\\"mvmaf_ori_v1080\\\":87.764,\\\"mvmaf_ori_v960\\\":90.444,\\\"mvmaf_ori_v864\\\":91.912,\\\"mvmaf_ori_v720\\\":94.301}\",\"ufq\":\"{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":68.944,\\\"srv1\\\":75.598},\\\"src\\\":77.18,\\\"trans\\\":68.944,\\\"v2.0\\\":{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":68.944,\\\"srv1\\\":76.156},\\\"src\\\":77.18,\\\"trans\\\":68.944,\\\"version\\\":\\\"v2.0\\\"},\\\"version\\\":\\\"v1.2_v2.0\\\"}\",\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 559110,
            "format": "dash",
            "gear_name": "720_2_1",
            "is_bytevc1": 1,
            "is_h265": 1,
            "play_addr": {
              "data_size": 10723249,
              "file_hash": "dabccfcb4d5554032b3487b617c45857",
              "height": 720,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_bytevc1_720p_559110",
              "url_list": [
                "https://v26-web.douyinvod.com/d7cff170baa60e4e687e8f36a8faed3b/69b2cc29/video/tos/cn/tos-cn-ve-15/oYA2f4PcGAKBwf7yAiLAGA7hBCevTABiIAAvBX/media-video-hvc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=546&bt=546&cs=4&ds=3&mime_type=video_mp4&qs=0&rc=Zjo4OmY3OGU3aDlnaDs4Z0BpMzNwbHg5cm52OTMzNGkzM0BjLmI1MWEtNl8xXmAxMTRiYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/867870aa409627b4d657aa01cdd4068c/69b2cc29/video/tos/cn/tos-cn-ve-15/oYA2f4PcGAKBwf7yAiLAGA7hBCevTABiIAAvBX/media-video-hvc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=546&bt=546&cs=4&ds=3&mime_type=video_mp4&qs=0&rc=Zjo4OmY3OGU3aDlnaDs4Z0BpMzNwbHg5cm52OTMzNGkzM0BjLmI1MWEtNl8xXmAxMTRiYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/dash/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=ed75484ecda64eb49b05d44c87e517fc&sign=dabccfcb4d5554032b3487b617c45857&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1280
            },
            "quality_type": 12,
            "video_extra": "{\"PktOffsetMap\":\"\",\"format\":\"dash\",\"sub_info\":\"{\\\"audio_bitrate_target\\\":64,\\\"audio_channels\\\":\\\"2.0\\\",\\\"audio_layout\\\":\\\"C\\\",\\\"audio_profile\\\":\\\"aac_he_v2\\\",\\\"audio_sample_rate\\\":\\\"44100\\\",\\\"base_range_info\\\":{\\\"first_moof_range\\\":\\\"1461-3232\\\",\\\"index_range\\\":\\\"1129-1460\\\",\\\"init_range\\\":\\\"0-1128\\\"},\\\"check_info\\\":{\\\"check_info\\\":\\\"a:v0300fg10000d6nsck7og65urivmh0e0|b:0-1460-058e979688bc235d9f4e376a3303eed3|c:0-1460-cec7\\\"},\\\"first_segment_range\\\":\\\"1461-340173\\\",\\\"gear_des_key\\\":\\\"0:DASH|1:normal|2:h265_hvc1|4:720p|5:normal|7:aweme/high_value_group-v3_0_r2|12:default|14:4|10000:105\\\",\\\"quality_type\\\":10}\",\"definition\":\"720p\",\"quality\":\"normal\",\"quality_desc\":\"\",\"file_id\":\"ed75484ecda64eb49b05d44c87e517fc\",\"real_bitrate\":622336,\"audio_file_id\":\"7f6191dfdd2a925abbecc2f33f564c53\",\"v_gear_id\":\"aweme/high_value_group-v3_0_r2\",\"u_vmaf\":94.3164,\"applog_map\":{\"feature_id\":\"df3fbde7708fa2439dee16087ff4afbc\"},\"sr_sharpness_strength\":8,\"mvmaf\":\"{\\\"mvmaf_sr_v1080\\\":96.021,\\\"mvmaf_sr_v960\\\":97.441,\\\"mvmaf_sr_v864\\\":98.063,\\\"mvmaf_sr_v720\\\":98.777,\\\"mvmaf_ori_v1080\\\":87.764,\\\"mvmaf_ori_v960\\\":90.444,\\\"mvmaf_ori_v864\\\":91.912,\\\"mvmaf_ori_v720\\\":94.301}\",\"ufq\":\"{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":68.944,\\\"srv1\\\":75.598},\\\"src\\\":77.18,\\\"trans\\\":68.944,\\\"v2.0\\\":{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":68.944,\\\"srv1\\\":76.156},\\\"src\\\":77.18,\\\"trans\\\":68.944,\\\"version\\\":\\\"v2.0\\\"},\\\"version\\\":\\\"v1.2_v2.0\\\"}\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 487991,
            "format": "mp4",
            "gear_name": "720_3_1",
            "is_bytevc1": 1,
            "is_h265": 1,
            "play_addr": {
              "data_size": 9359311,
              "file_cs": "c:0-127958-7e6e|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "9b24eb10a47e8e0eb827b0c2a44008b2",
              "height": 720,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_bytevc1_720p_487991",
              "url_list": [
                "https://v26-web.douyinvod.com/969e26343eaa2240163edb8597bdad41/69b1a4d9/video/tos/cn/tos-cn-ve-15/oYjsBKIRgBAThpXev9LGff6iw8y29BAcGC47A7/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=476&bt=476&cs=2&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=ZzZnOWgzNmlnZDc6Mzo3NEBpMzNwbHg5cm52OTMzNGkzM0BiMy5iNWNiNmAxYzA2MC40YSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100H_100K_100o_100w_100B&dy_q=1773238800&feature_id=df3fbde7708fa2439dee16087ff4afbc&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/ead168cd9c93103b5db49573cd3b2fb9/69b1a4d9/video/tos/cn/tos-cn-ve-15/oYjsBKIRgBAThpXev9LGff6iw8y29BAcGC47A7/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=476&bt=476&cs=2&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=ZzZnOWgzNmlnZDc6Mzo3NEBpMzNwbHg5cm52OTMzNGkzM0BiMy5iNWNiNmAxYzA2MC40YSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100o_100w_100B_100H_100K&dy_q=1773238800&feature_id=df3fbde7708fa2439dee16087ff4afbc&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=7114379037f44acd9fe3f08ee61d63ed&sign=9b24eb10a47e8e0eb827b0c2a44008b2&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1280
            },
            "quality_type": 13,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 199715}, {\\\"time\\\": 2, \\\"offset\\\": 268276}, {\\\"time\\\": 3, \\\"offset\\\": 296155}, {\\\"time\\\": 4, \\\"offset\\\": 350701}, {\\\"time\\\": 5, \\\"offset\\\": 382302}, {\\\"time\\\": 10, \\\"offset\\\": 567068}]\",\"format\":\"mp4\",\"definition\":\"720p\",\"quality\":\"normal\",\"file_id\":\"7114379037f44acd9fe3f08ee61d63ed\",\"v_gear_id\":\"aweme/high_value_group-v3_0_r2\",\"u_vmaf\":91.0903,\"applog_map\":{\"feature_id\":\"df3fbde7708fa2439dee16087ff4afbc\"},\"sr_sharpness_strength\":8,\"mvmaf\":\"{\\\"mvmaf_sr_v1080\\\":92.56,\\\"mvmaf_sr_v960\\\":94.107,\\\"mvmaf_sr_v864\\\":94.755,\\\"mvmaf_sr_v720\\\":95.98,\\\"mvmaf_ori_v1080\\\":83.628,\\\"mvmaf_ori_v960\\\":86.688,\\\"mvmaf_ori_v864\\\":88.365,\\\"mvmaf_ori_v720\\\":91.174}\",\"ufq\":\"{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":65.151,\\\"srv1\\\":72.954},\\\"src\\\":77.18,\\\"trans\\\":65.151,\\\"v2.0\\\":{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":65.151,\\\"srv1\\\":72.676},\\\"src\\\":77.18,\\\"trans\\\":65.151,\\\"version\\\":\\\"v2.0\\\"},\\\"version\\\":\\\"v1.2_v2.0\\\"}\",\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 425271,
            "format": "mp4",
            "gear_name": "720_4_1",
            "is_bytevc1": 1,
            "is_h265": 1,
            "play_addr": {
              "data_size": 8156385,
              "file_cs": "c:0-127958-42a1|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "e3bfcc33610f61352f32a2107e0ef88d",
              "height": 720,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_bytevc1_720p_425271",
              "url_list": [
                "https://v26-web.douyinvod.com/e53da37e1670c8ea380e31dd5c068eb3/69b1a4d9/video/tos/cn/tos-cn-ve-15/oQf9fAE6xMAttgimavv4CEA5DeRHlhAy2fYtDD/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=415&bt=415&cs=2&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=NTM5aWk1OjQ3PGY2aDtmN0BpMzNwbHg5cm52OTMzNGkzM0AxMjM0Ni5gNWMxLjU0M2I0YSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100H_100K_100o_100w_100B&dy_q=1773238800&feature_id=df3fbde7708fa2439dee16087ff4afbc&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/f8dc8f3b546090f00c562f3f15c93144/69b1a4d9/video/tos/cn/tos-cn-ve-15/oQf9fAE6xMAttgimavv4CEA5DeRHlhAy2fYtDD/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=415&bt=415&cs=2&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=NTM5aWk1OjQ3PGY2aDtmN0BpMzNwbHg5cm52OTMzNGkzM0AxMjM0Ni5gNWMxLjU0M2I0YSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=df3fbde7708fa2439dee16087ff4afbc&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=74ec687e831c49f481c7129f7f665042&sign=e3bfcc33610f61352f32a2107e0ef88d&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1280
            },
            "quality_type": 14,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 196733}, {\\\"time\\\": 2, \\\"offset\\\": 262794}, {\\\"time\\\": 3, \\\"offset\\\": 289708}, {\\\"time\\\": 4, \\\"offset\\\": 342049}, {\\\"time\\\": 5, \\\"offset\\\": 370774}, {\\\"time\\\": 10, \\\"offset\\\": 528902}]\",\"format\":\"mp4\",\"definition\":\"720p\",\"quality\":\"normal\",\"file_id\":\"74ec687e831c49f481c7129f7f665042\",\"v_gear_id\":\"aweme/high_value_group-v3_0_r2\",\"u_vmaf\":88.1067,\"applog_map\":{\"feature_id\":\"df3fbde7708fa2439dee16087ff4afbc\"},\"sr_sharpness_strength\":8,\"mvmaf\":\"{\\\"mvmaf_sr_v1080\\\":90.248,\\\"mvmaf_sr_v960\\\":91.496,\\\"mvmaf_sr_v864\\\":92.337,\\\"mvmaf_sr_v720\\\":93.681,\\\"mvmaf_ori_v1080\\\":80.992,\\\"mvmaf_ori_v960\\\":84.057,\\\"mvmaf_ori_v864\\\":85.602,\\\"mvmaf_ori_v720\\\":88.78}\",\"ufq\":\"{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":63.174,\\\"srv1\\\":70.642},\\\"src\\\":77.18,\\\"trans\\\":63.174,\\\"v2.0\\\":{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":63.174,\\\"srv1\\\":70.841},\\\"src\\\":77.18,\\\"trans\\\":63.174,\\\"version\\\":\\\"v2.0\\\"},\\\"version\\\":\\\"v1.2_v2.0\\\"}\",\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 424765,
            "format": "dash",
            "gear_name": "720_3_1",
            "is_bytevc1": 1,
            "is_h265": 1,
            "play_addr": {
              "data_size": 8146615,
              "file_hash": "8223a046cbef2290002394b4e9bb1766",
              "height": 720,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_bytevc1_720p_424765",
              "url_list": [
                "https://v26-web.douyinvod.com/4d77c206eb56fb750c737ee642078970/69b2cc29/video/tos/cn/tos-cn-ve-15/oAiBfLTc4CABB2GEAeoaAGAAIH9vwX7hiKyA7e/media-video-hvc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=414&bt=414&cs=4&ds=3&mime_type=video_mp4&qs=0&rc=aWlpPGk8aDY5NjgzNzw5ZEBpMzNwbHg5cm52OTMzNGkzM0A0NWAyXjZeXi8xX15fXzVjYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/56cb1c004bdf738e31a6c04bfeed8bae/69b2cc29/video/tos/cn/tos-cn-ve-15/oAiBfLTc4CABB2GEAeoaAGAAIH9vwX7hiKyA7e/media-video-hvc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=414&bt=414&cs=4&ds=3&mime_type=video_mp4&qs=0&rc=aWlpPGk8aDY5NjgzNzw5ZEBpMzNwbHg5cm52OTMzNGkzM0A0NWAyXjZeXi8xX15fXzVjYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100o_100w&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/dash/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=fa86b9b4a0b54326a3ae99af59cf8f7f&sign=8223a046cbef2290002394b4e9bb1766&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1280
            },
            "quality_type": 13,
            "video_extra": "{\"PktOffsetMap\":\"\",\"format\":\"dash\",\"sub_info\":\"{\\\"audio_bitrate_target\\\":64,\\\"audio_channels\\\":\\\"2.0\\\",\\\"audio_layout\\\":\\\"C\\\",\\\"audio_profile\\\":\\\"aac_he_v2\\\",\\\"audio_sample_rate\\\":\\\"44100\\\",\\\"base_range_info\\\":{\\\"first_moof_range\\\":\\\"1461-3232\\\",\\\"index_range\\\":\\\"1129-1460\\\",\\\"init_range\\\":\\\"0-1128\\\"},\\\"check_info\\\":{\\\"check_info\\\":\\\"a:v0300fg10000d6nsck7og65urivmh0e0|b:0-1460-f6fa632689cc5169f47c15c85eaac34b|c:0-1460-debc\\\"},\\\"first_segment_range\\\":\\\"1461-249122\\\",\\\"gear_des_key\\\":\\\"0:DASH|1:normal|2:h265_hvc1|4:720p|5:normal|7:aweme/high_value_group-v3_0_r2|12:default|14:5|10000:105\\\",\\\"quality_type\\\":10}\",\"definition\":\"720p\",\"quality\":\"normal\",\"quality_desc\":\"\",\"file_id\":\"fa86b9b4a0b54326a3ae99af59cf8f7f\",\"real_bitrate\":487991,\"audio_file_id\":\"7f6191dfdd2a925abbecc2f33f564c53\",\"v_gear_id\":\"aweme/high_value_group-v3_0_r2\",\"u_vmaf\":91.0903,\"applog_map\":{\"feature_id\":\"df3fbde7708fa2439dee16087ff4afbc\"},\"sr_sharpness_strength\":8,\"mvmaf\":\"{\\\"mvmaf_sr_v1080\\\":92.56,\\\"mvmaf_sr_v960\\\":94.107,\\\"mvmaf_sr_v864\\\":94.755,\\\"mvmaf_sr_v720\\\":95.98,\\\"mvmaf_ori_v1080\\\":83.628,\\\"mvmaf_ori_v960\\\":86.688,\\\"mvmaf_ori_v864\\\":88.365,\\\"mvmaf_ori_v720\\\":91.174}\",\"ufq\":\"{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":65.151,\\\"srv1\\\":72.954},\\\"src\\\":77.18,\\\"trans\\\":65.151,\\\"v2.0\\\":{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":65.151,\\\"srv1\\\":72.676},\\\"src\\\":77.18,\\\"trans\\\":65.151,\\\"version\\\":\\\"v2.0\\\"},\\\"version\\\":\\\"v1.2_v2.0\\\"}\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 418950,
            "format": "mp4",
            "gear_name": "540_3_1",
            "is_bytevc1": 1,
            "is_h265": 1,
            "play_addr": {
              "data_size": 8035153,
              "file_cs": "c:0-127958-7958|a:v0300fg10000d6nsck7og65urivmh0e0",
              "file_hash": "4fb162167fefdd95770f9c791a8a6ac7",
              "height": 576,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_bytevc1_540p_418950",
              "url_list": [
                "https://v26-web.douyinvod.com/939f9a9beeb4591afc8539d5cfca2565/69b1a4d9/video/tos/cn/tos-cn-ve-15/oQ42ADlyhtDpqeffD6ESiuteAAvzCmAxvEYMpg/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=409&bt=409&cs=2&ds=6&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=ZTk6ZjlpOjU7ZTs0Mzw0NUBpMzNwbHg5cm52OTMzNGkzM0AxMjE2XmFeXjUxMjM2X2BjYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=58cda3e98e752cb3d8fb6e2ff26523b0&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/6af26fa956abd35c6aaa435864ed4cca/69b1a4d9/video/tos/cn/tos-cn-ve-15/oQ42ADlyhtDpqeffD6ESiuteAAvzCmAxvEYMpg/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=409&bt=409&cs=2&ds=6&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=ZTk6ZjlpOjU7ZTs0Mzw0NUBpMzNwbHg5cm52OTMzNGkzM0AxMjE2XmFeXjUxMjM2X2BjYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100B_100H_100K_100o_100w&dy_q=1773238800&feature_id=58cda3e98e752cb3d8fb6e2ff26523b0&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=f2c1b99061584b88a2a7dfa69c47564b&sign=4fb162167fefdd95770f9c791a8a6ac7&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1024
            },
            "quality_type": 24,
            "video_extra": "{\"PktOffsetMap\":\"[{\\\"time\\\": 1, \\\"offset\\\": 185634}, {\\\"time\\\": 2, \\\"offset\\\": 243990}, {\\\"time\\\": 3, \\\"offset\\\": 269599}, {\\\"time\\\": 4, \\\"offset\\\": 313261}, {\\\"time\\\": 5, \\\"offset\\\": 340786}, {\\\"time\\\": 10, \\\"offset\\\": 498614}]\",\"format\":\"mp4\",\"definition\":\"540p\",\"quality\":\"normal\",\"file_id\":\"f2c1b99061584b88a2a7dfa69c47564b\",\"v_gear_id\":\"aweme/high_value_group-v3_0_r2\",\"u_vmaf\":90.8765,\"applog_map\":{\"feature_id\":\"58cda3e98e752cb3d8fb6e2ff26523b0\"},\"sr_sharpness_strength\":8,\"mvmaf\":\"{\\\"mvmaf_sr_v1080\\\":89.654,\\\"mvmaf_sr_v960\\\":91.603,\\\"mvmaf_sr_v864\\\":92.386,\\\"mvmaf_sr_v720\\\":93.187,\\\"mvmaf_ori_v1080\\\":79.095,\\\"mvmaf_ori_v960\\\":82.418,\\\"mvmaf_ori_v864\\\":84.239,\\\"mvmaf_ori_v720\\\":87.144}\",\"ufq\":\"{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":61.751,\\\"srv1\\\":70.048},\\\"src\\\":77.18,\\\"trans\\\":61.751,\\\"v2.0\\\":{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":61.751,\\\"srv1\\\":69.948},\\\"src\\\":77.18,\\\"trans\\\":61.751,\\\"version\\\":\\\"v2.0\\\"},\\\"version\\\":\\\"v1.2_v2.0\\\"}\",\"audio_channels\":\"2.0\",\"audio_sample_rate\":\"44100\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 366386,
            "format": "dash",
            "gear_name": "540_3_1",
            "is_bytevc1": 1,
            "is_h265": 1,
            "play_addr": {
              "data_size": 7026957,
              "file_hash": "26a374ab0acd5a4a675006b9129f4509",
              "height": 576,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_bytevc1_540p_366386",
              "url_list": [
                "https://v26-web.douyinvod.com/75c21a8f6d80a3d46ae38b99e7b8b670/69b2cc29/video/tos/cn/tos-cn-ve-15/oAAABBmTGIhAA7fAywfeCL9XKGlvc4i7Byi2vA/media-video-hvc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=357&bt=357&cs=4&ds=6&mime_type=video_mp4&qs=0&rc=Njk8ZzxnNTwzNDY4NjpnZEBpMzNwbHg5cm52OTMzNGkzM0AzNmEyYDIuNS8xMzI0YWMvYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/e0f12a7896abf2bf6d8c6859fa9065d8/69b2cc29/video/tos/cn/tos-cn-ve-15/oAAABBmTGIhAA7fAywfeCL9XKGlvc4i7Byi2vA/media-video-hvc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=357&bt=357&cs=4&ds=6&mime_type=video_mp4&qs=0&rc=Njk8ZzxnNTwzNDY4NjpnZEBpMzNwbHg5cm52OTMzNGkzM0AzNmEyYDIuNS8xMzI0YWMvYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100o_100w&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/dash/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=2afdd7735563412089125dc95dd99663&sign=26a374ab0acd5a4a675006b9129f4509&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1024
            },
            "quality_type": 24,
            "video_extra": "{\"PktOffsetMap\":\"\",\"format\":\"dash\",\"sub_info\":\"{\\\"audio_bitrate_target\\\":48,\\\"audio_channels\\\":\\\"2.0\\\",\\\"audio_layout\\\":\\\"C\\\",\\\"audio_profile\\\":\\\"aac_he_v2\\\",\\\"audio_sample_rate\\\":\\\"44100\\\",\\\"base_range_info\\\":{\\\"first_moof_range\\\":\\\"1461-3232\\\",\\\"index_range\\\":\\\"1129-1460\\\",\\\"init_range\\\":\\\"0-1128\\\"},\\\"check_info\\\":{\\\"check_info\\\":\\\"a:v0300fg10000d6nsck7og65urivmh0e0|b:0-1460-dc69c49e7a6fabb8cd4477bbdc6eba35|c:0-1460-433b\\\"},\\\"first_segment_range\\\":\\\"1461-202636\\\",\\\"gear_des_key\\\":\\\"0:DASH|1:normal|2:h265_hvc1|4:540p|5:normal|7:aweme/high_value_group-v3_0_r2|12:default|14:8|10000:105\\\",\\\"quality_type\\\":20}\",\"definition\":\"540p\",\"quality\":\"normal\",\"quality_desc\":\"\",\"file_id\":\"2afdd7735563412089125dc95dd99663\",\"real_bitrate\":418950,\"audio_file_id\":\"7f6191dfdd2a925abbecc2f33f564c53\",\"v_gear_id\":\"aweme/high_value_group-v3_0_r2\",\"u_vmaf\":90.8765,\"applog_map\":{\"feature_id\":\"58cda3e98e752cb3d8fb6e2ff26523b0\"},\"sr_sharpness_strength\":8,\"mvmaf\":\"{\\\"mvmaf_sr_v1080\\\":89.654,\\\"mvmaf_sr_v960\\\":91.603,\\\"mvmaf_sr_v864\\\":92.386,\\\"mvmaf_sr_v720\\\":93.187,\\\"mvmaf_ori_v1080\\\":79.095,\\\"mvmaf_ori_v960\\\":82.418,\\\"mvmaf_ori_v864\\\":84.239,\\\"mvmaf_ori_v720\\\":87.144}\",\"ufq\":\"{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":61.751,\\\"srv1\\\":70.048},\\\"src\\\":77.18,\\\"trans\\\":61.751,\\\"v2.0\\\":{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":61.751,\\\"srv1\\\":69.948},\\\"src\\\":77.18,\\\"trans\\\":61.751,\\\"version\\\":\\\"v2.0\\\"},\\\"version\\\":\\\"v1.2_v2.0\\\"}\",\"audio_metrics\":null,\"audio_score\":null}"
          },
          {
            "FPS": 30,
            "HDR_bit": "",
            "HDR_type": "",
            "bit_rate": 362044,
            "format": "dash",
            "gear_name": "720_4_1",
            "is_bytevc1": 1,
            "is_h265": 1,
            "play_addr": {
              "data_size": 6943689,
              "file_hash": "0787417099ae2316032208aad6f17e88",
              "height": 720,
              "uri": "v0300fg10000d6nsck7og65urivmh0e0",
              "url_key": "v0300fg10000d6nsck7og65urivmh0e0_bytevc1_720p_362044",
              "url_list": [
                "https://v26-web.douyinvod.com/a25471d8bee1c59fb150c5f194ea08cf/69b2cc29/video/tos/cn/tos-cn-ve-15/ogBf74wZTiGABAB7eCXLZKyIA2GAcxAihAey9v/media-video-hvc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=353&bt=353&cs=4&ds=3&mime_type=video_mp4&qs=0&rc=PDo1OWY6ZjM7OjY3aWloPEBpMzNwbHg5cm52OTMzNGkzM0AzNTBiX14xXjIxMjMtMzZjYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://v11-weba.douyinvod.com/1e0516f64f4136daf86acb23d8cbbb89/69b2cc29/video/tos/cn/tos-cn-ve-15/ogBf74wZTiGABAB7eCXLZKyIA2GAcxAihAey9v/media-video-hvc1/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=353&bt=353&cs=4&ds=3&mime_type=video_mp4&qs=0&rc=PDo1OWY6ZjM7OjY3aWloPEBpMzNwbHg5cm52OTMzNGkzM0AzNTBiX14xXjIxMjMtMzZjYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "https://www.douyin.com/aweme/v1/play/dash/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=f99e6f0f64534758a04ca7bce6328769&sign=0787417099ae2316032208aad6f17e88&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL"
              ],
              "width": 1280
            },
            "quality_type": 14,
            "video_extra": "{\"PktOffsetMap\":\"\",\"format\":\"dash\",\"sub_info\":\"{\\\"audio_bitrate_target\\\":64,\\\"audio_channels\\\":\\\"2.0\\\",\\\"audio_layout\\\":\\\"C\\\",\\\"audio_profile\\\":\\\"aac_he_v2\\\",\\\"audio_sample_rate\\\":\\\"44100\\\",\\\"base_range_info\\\":{\\\"first_moof_range\\\":\\\"1461-3232\\\",\\\"index_range\\\":\\\"1129-1460\\\",\\\"init_range\\\":\\\"0-1128\\\"},\\\"check_info\\\":{\\\"check_info\\\":\\\"a:v0300fg10000d6nsck7og65urivmh0e0|b:0-1460-24c1845b7d9a73c88421c48a26bdaf17|c:0-1460-266e\\\"},\\\"first_segment_range\\\":\\\"1461-236963\\\",\\\"gear_des_key\\\":\\\"0:DASH|1:normal|2:h265_hvc1|4:720p|5:normal|7:aweme/high_value_group-v3_0_r2|12:default|14:6|10000:105\\\",\\\"quality_type\\\":10}\",\"definition\":\"720p\",\"quality\":\"normal\",\"quality_desc\":\"\",\"file_id\":\"f99e6f0f64534758a04ca7bce6328769\",\"real_bitrate\":425271,\"audio_file_id\":\"7f6191dfdd2a925abbecc2f33f564c53\",\"v_gear_id\":\"aweme/high_value_group-v3_0_r2\",\"u_vmaf\":88.1067,\"applog_map\":{\"feature_id\":\"df3fbde7708fa2439dee16087ff4afbc\"},\"sr_sharpness_strength\":8,\"mvmaf\":\"{\\\"mvmaf_sr_v1080\\\":90.248,\\\"mvmaf_sr_v960\\\":91.496,\\\"mvmaf_sr_v864\\\":92.337,\\\"mvmaf_sr_v720\\\":93.681,\\\"mvmaf_ori_v1080\\\":80.992,\\\"mvmaf_ori_v960\\\":84.057,\\\"mvmaf_ori_v864\\\":85.602,\\\"mvmaf_ori_v720\\\":88.78}\",\"ufq\":\"{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":63.174,\\\"srv1\\\":70.642},\\\"src\\\":77.18,\\\"trans\\\":63.174,\\\"v2.0\\\":{\\\"enh\\\":77.18,\\\"playback\\\":{\\\"ori\\\":63.174,\\\"srv1\\\":70.841},\\\"src\\\":77.18,\\\"trans\\\":63.174,\\\"version\\\":\\\"v2.0\\\"},\\\"version\\\":\\\"v1.2_v2.0\\\"}\",\"audio_metrics\":null,\"audio_score\":null}"
          }
        ],
        "download_addr": {
          "data_size": 39919936,
          "file_cs": "c:0-165954-1cf3",
          "height": 720,
          "uri": "v0300fg10000d6nsck7og65urivmh0e0",
          "url_list": [
            "https://v26-web.douyinvod.com/0d46f2b0a89961c6fa39d580aad67438/69b1a4d9/video/tos/cn/tos-cn-ve-15/o0IEvpoAzB8mjg9Le9EAiFEAZyDdgrQV3fcQHD/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1993&bt=1993&cs=0&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=OjNnPGk1NzhnPDlpNDc4O0BpMzNwbHg5cm52OTMzNGkzM0BgLjFjMDAzXjAxXmMtMTEzYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100B_100H_100K_100o&dy_q=1773238800&feature_id=dc6e471fd69cf67451806384e04f2b47&l=2026031122200054888BD886C03C0761C0",
            "https://v11-weba.douyinvod.com/0474be215d342a65eb72c96bcc633234/69b1a4d9/video/tos/cn/tos-cn-ve-15/o0IEvpoAzB8mjg9Le9EAiFEAZyDdgrQV3fcQHD/?a=6383&ch=26&cr=3&dr=0&lr=all&cd=0%7C0%7C0%7C3&cv=1&br=1993&bt=1993&cs=0&ds=3&ft=rVWEerwwZRcZseFo1PDS6kwc3RZtGOxgGg9eFXR0hqO12nzXT&mime_type=video_mp4&qs=0&rc=OjNnPGk1NzhnPDlpNDc4O0BpMzNwbHg5cm52OTMzNGkzM0BgLjFjMDAzXjAxXmMtMTEzYSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100K_100o_100w_100B_100H&dy_q=1773238800&feature_id=dc6e471fd69cf67451806384e04f2b47&l=2026031122200054888BD886C03C0761C0",
            "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&ratio=540p&watermark=1&media_type=4&vr_type=0&improve_bitrate=0&biz_sign=NRPI1lC4AlkgB1YZ_o3JxvAfESRKvePEcuaVqM03fyqwTcZpHItYhscforGE9VaaOzLa7CA0Ou-V1V2BgVgRf9Ub5U_FDGbNHaUZIZuaRMUGyO7s5wbVW7et0I2xs1hA&logo_name=aweme_search_suffix&source=PackSourceEnum_AWEME_DETAIL"
          ],
          "width": 720
        },
        "bit_rate_audio": [
          {
            "audio_extra": "{\"real_bitrate\":589356}",
            "audio_meta": {
              "bitrate": 59997,
              "codec_type": "bytevc1",
              "encoded_type": "normal",
              "file_hash": "7f6191dfdd2a925abbecc2f33f564c53",
              "file_id": "7f6191dfdd2a925abbecc2f33f564c53",
              "format": "dash",
              "fps": 0,
              "logo_type": "",
              "media_type": "audio",
              "quality": "normal",
              "quality_desc": "",
              "size": 1150557,
              "sub_info": "{\"audio_bitrate_target\":64,\"audio_channels\":\"2.0\",\"audio_layout\":\"C\",\"audio_profile\":\"aac_he_v2\",\"audio_sample_rate\":\"44100\",\"base_range_info\":{\"index_range\":\"866-1209\",\"init_range\":\"0-865\"},\"check_info\":{\"check_info\":\"a:v0300fg10000d6nsck7og65urivmh0e0|b:0-1209-54d5d1ccb542c852ff39e0d531618ce5|c:0-1209-afc2\"},\"first_segment_range\":\"1210-47117\",\"gear_des_key\":\"0:dash|1:normal|2:bytevc1|5:normal|10000:105\"}",
              "url_list": {
                "backup_url": "https://v11-weba.douyinvod.com/cfd23440d98c43b687144f45a311e10e/69b2cc29/video/tos/cn/tos-cn-ve-15/o4AAfLihyXc5GeOA7evC4kB7AGIiBKAABTR2iw/media-audio-und-mp4a/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=58&bt=58&cs=4&mime_type=video_mp4&qs=0&rc=NmY5aTY1ZmVkNWRnaTQ0aUBpMzNwbHg5cm52OTMzNGkzM0AyMTIwY2BiXzI2L2FhNjM0YSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0",
                "fallback_url": "https://www.douyin.com/aweme/v1/play/?video_id=v0300fg10000d6nsck7og65urivmh0e0&line=0&file_id=7f6191dfdd2a925abbecc2f33f564c53&sign=7f6191dfdd2a925abbecc2f33f564c53&is_play_url=1&source=PackSourceEnum_AWEME_DETAIL",
                "main_url": "https://v26-web.douyinvod.com/4c47104de91512c39549d6d7b2d75742/69b2cc29/video/tos/cn/tos-cn-ve-15/o4AAfLihyXc5GeOA7evC4kB7AGIiBKAABTR2iw/media-audio-und-mp4a/?a=6383&ch=0&cr=8&dr=0&er=1&lr=default&cd=0%7C0%7C0%7C3&cv=1&br=58&bt=58&cs=4&mime_type=video_mp4&qs=0&rc=NmY5aTY1ZmVkNWRnaTQ0aUBpMzNwbHg5cm52OTMzNGkzM0AyMTIwY2BiXzI2L2FhNjM0YSNiZXNvMmQ0YGthLS1kLTBzcw%3D%3D&btag=80000e00028000&cquery=100w_100o&dy_q=1773238800&l=2026031122200054888BD886C03C0761C0"
              }
            },
            "audio_quality": 6
          }
        ]
      },
      "music": {
        "author": "陨落的魂殿萧天尊",
        "extra": "{\"aed_singing_score\":0.01,\"aggregate_exempt_conf\":[],\"beats\":{},\"cover_colors\":null,\"douyin_beats_info\":{},\"dsp_switch\":0,\"extract_item_id\":7615525123759607094,\"has_edited\":0,\"hotsoon_review_time\":-1,\"is_aed_music\":0,\"is_red\":0,\"is_subsidy_exp\":false,\"music_label_id\":null,\"music_tagging\":{\"AEDs\":null,\"Genres\":null,\"Instruments\":null,\"Languages\":null,\"Moods\":null,\"SingingVersions\":null,\"Themes\":null},\"review_unshelve_reason\":0,\"reviewed\":0,\"schedule_search_time\":0,\"uniqa_speech_score\":0.68,\"with_aed_model\":0}",
        "id": 7615525231448378163,
        "play_url": "https://sf5-hl-ali-cdn-tos.douyinstatic.com/obj/ies-music/7615525266907138866.mp3",
        "title": "@陨落的魂殿萧天尊创作的原声",
        "cover": "https://p3-pc.douyinpic.com/aweme/1080x1080/aweme-avatar/tos-cn-i-c9aec8xkvj_b5bb78872e6d4d14ae8993ba490a72cc.jpeg?from=327834062",
        "cover_hd": {
          "height": 720,
          "width": 720,
          "url_list": [
            "https://p3-pc.douyinpic.com/aweme/1080x1080/aweme-avatar/tos-cn-i-c9aec8xkvj_b5bb78872e6d4d14ae8993ba490a72cc.jpeg?from=327834062"
          ]
        }
      },
      "cover": "https://p3-pc-sign.douyinpic.com/obj/tos-cn-i-dy/f5ca3781b88d4248b09dc0c12f3b75c0?lk3s=138a59ce&x-expires=1774447200&x-signature=n6IZRWiedU9C9bmdwfK0BBz6MqE%3D&from=327834062_large&s=PackSourceEnum_AWEME_DETAIL&se=false&sc=dynamic_cover&biz_tag=pcweb_cover&l=2026031122200054888BD886C03C0761C0",
      "media_type": 1,
      "aweme_link": "https://www.douyin.com/video/7615525123759607094"
    }
  },
  "time": 0.31543993949890137
}
]]

-- 长按管理
local longPressHandler = Handler()
local currentLongPressPath = nil

local longPressRunnable = Runnable{
  run=function()
    if currentLongPressPath then
      local clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
      local clip = ClipData.newPlainText("JSON_PATH", currentLongPressPath)
      clipboard.setPrimaryClip(clip)
      print("路径已复制: " .. currentLongPressPath)
    end
  end
}

-- 状态管理
local TreeManager = {
  rawData = dkjson.decode(jsonStr),
  visibleNodes = {},
  baseLineHeight = 60,
  indentSize = 50,
  nodeStates = {},
  scrollY = 0,
  maxScrollY = 0,
  scrollX = 0,
  maxScrollX = 0,
}

local paint = Paint()
paint.setAntiAlias(true)
paint.setTextSize(36)
paint.setTypeface(Typeface.MONOSPACE)

function TreeManager:build(data, depth, path, list)
  local keys = {}
  if type(data) == "table" then
    for k in pairs(data) do table.insert(keys, k) end
    table.sort(keys, function(a,b) return tostring(a) < tostring(b) end)
  end

  for _, k in ipairs(keys) do
    local v = data[k]
    local currentPath
    local keyStr = tostring(k)
    if path == "" then
      currentPath = keyStr
     else
      if type(k) == "number" then
        currentPath = path .. "[" .. keyStr .. "]"
       else
        currentPath = path .. "." .. keyStr
      end
    end
    local isNull = (v == dkjson.null)
    local isTable = (type(v) == "table" and not isNull)

    local typeName = ""
    if isTable then
      typeName = (v[1] ~= nil or #v > 0) and "Array" or "Object"
     elseif isNull then
      typeName = "null"
    end

    local node = {
      key = k,
      value = isNull and "null" or tostring(v),
      depth = depth,
      isTable = isTable,
      typeName = typeName,
      path = currentPath,
      expanded = self.nodeStates[currentPath] or false,
      height = self.baseLineHeight
    }
    table.insert(list, node)
    if isTable and node.expanded then
      self:build(v, depth + 1, currentPath, list)
    end
  end
end

function refreshData()
  local newList = {}
  TreeManager:build(TreeManager.rawData, 0, "", newList)
  TreeManager.visibleNodes = newList

  local metrics = activity.getResources().getDisplayMetrics()
  local screenWidth = metrics.widthPixels
  local viewHeight = (dview.getHeight() > 0) and dview.getHeight() or metrics.heightPixels

  local paddingRight = 100
  local startXBase = 80
  local marginBottom = 150
  local startYBase = 80

  local totalH = 0
  local maxContentWidth = screenWidth

  for i, node in ipairs(TreeManager.visibleNodes) do
    local x = startXBase + (node.depth * TreeManager.indentSize)

    node.height = TreeManager.baseLineHeight

    local keyText = tostring(node.key) .. " : "
    local keyWidth = paint.measureText(keyText)

    local contentWidth = 0
    if node.isTable then
      contentWidth = paint.measureText(node.typeName)
     else
      contentWidth = paint.measureText(node.value)
    end

    local rowW = x + keyWidth + contentWidth + paddingRight
    if rowW > maxContentWidth then maxContentWidth = rowW end

    totalH = totalH + node.height
  end

  local finalContentHeight = totalH + startYBase + marginBottom
  TreeManager.maxScrollY = math.max(0, finalContentHeight - viewHeight)
  TreeManager.maxScrollX = math.max(0, maxContentWidth - screenWidth)

  TreeManager.scrollY = math.min(TreeManager.scrollY, TreeManager.maxScrollY)
  TreeManager.scrollX = math.min(TreeManager.scrollX, TreeManager.maxScrollX)
end

layout = {
  FrameLayout,
  layout_width="fill",
  layout_height="fill",
  backgroundColor="#1a1a1b",
  {
    View,
    id="dview",
    layout_width="fill",
    layout_height="fill",
  },
}
activity.setContentView(loadlayout(layout))

-- 绘制
local drawable = LuaDrawable(function(canvas, p, view)
  canvas.save()
  canvas.translate(-TreeManager.scrollX, -TreeManager.scrollY)

  local curY = 80
  for i, node in ipairs(TreeManager.visibleNodes) do
    if (curY - TreeManager.scrollY < canvas.getHeight()) and (curY + node.height - TreeManager.scrollY > 0) then
      local x = 80 + (node.depth * TreeManager.indentSize)
      local midY = curY + (TreeManager.baseLineHeight / 2)

      if node.isTable then
        paint.setColor(0xFF999999)
        local path = Path()
        if node.expanded then
          path.moveTo(x - 40, midY - 10); path.lineTo(x - 25, midY + 5); path.lineTo(x - 10, midY - 10)
         else
          path.moveTo(x - 35, midY - 15); path.lineTo(x - 20, midY); path.lineTo(x - 35, midY + 15)
        end
        canvas.drawPath(path, paint)
      end

      paint.setColor(0xFFE26AB5)
      local keyText = tostring(node.key) .. " : "
      canvas.drawText(keyText, x, midY + 12, paint)
      local keyWidth = paint.measureText(keyText)

      if node.isTable then
        paint.setColor(0xFFBBBBBB)
        canvas.drawText(node.typeName, x + keyWidth, midY + 12, paint)
       else
        if node.value == "null" then paint.setColor(0xFFEF5350)
         elseif tonumber(node.value) then paint.setColor(0xFF4DB6AC)
         else paint.setColor(0xFF81C784) end
        canvas.drawText(node.value, x + keyWidth, midY + 12, paint)
      end
    end
    curY = curY + node.height
  end
  canvas.restore()
end)

dview.setBackground(drawable)

-- 惯性滑动
local scroller = OverScroller(activity)
local velocityTracker = VelocityTracker.obtain()

local function computeScroll()
  if scroller.computeScrollOffset() then
    TreeManager.scrollX = math.min(math.max(0, scroller.getCurrX()), TreeManager.maxScrollX)
    TreeManager.scrollY = math.min(math.max(0, scroller.getCurrY()), TreeManager.maxScrollY)
    dview.invalidate()
    dview.postOnAnimation(computeScroll)
  end
end

local lastX, lastY = 0, 0
local downX, downY = 0, 0
local isClick = true
local touchSlop = 15

dview.onTouch = function(v, event)
  velocityTracker.addMovement(event)
  local action = event.getAction()
  local x, y = event.getX(), event.getY()

  if action == MotionEvent.ACTION_DOWN then
    if not scroller.isFinished() then scroller.forceFinished(true) end
    lastX, lastY = x, y
    downX, downY = x, y
    isClick = true

    local clickY = y + TreeManager.scrollY
    local accumY = 80
    currentLongPressPath = nil
    for _, node in ipairs(TreeManager.visibleNodes) do
      if clickY >= accumY and clickY <= (accumY + node.height) then
        currentLongPressPath = node.path
        break
      end
      accumY = accumY + node.height
    end
    longPressHandler.postDelayed(longPressRunnable, 500)
    return true
   elseif action == MotionEvent.ACTION_MOVE then
    local dx, dy = x - lastX, y - lastY
    TreeManager.scrollX = math.min(math.max(0, TreeManager.scrollX - dx), TreeManager.maxScrollX)
    TreeManager.scrollY = math.min(math.max(0, TreeManager.scrollY - dy), TreeManager.maxScrollY)
    lastX, lastY = x, y
    if math.abs(x - downX) > touchSlop or math.abs(y - downY) > touchSlop then
      isClick = false
      longPressHandler.removeCallbacks(longPressRunnable)
    end
    v.invalidate()
   elseif action == MotionEvent.ACTION_UP or action == MotionEvent.ACTION_CANCEL then
    longPressHandler.removeCallbacks(longPressRunnable)
    if isClick and action == MotionEvent.ACTION_UP then
      local clickX, clickY = x + TreeManager.scrollX, y + TreeManager.scrollY
      local accumY = 80
      for _, node in ipairs(TreeManager.visibleNodes) do
        if clickY >= accumY and clickY <= (accumY + node.height) then
          if node.isTable then
            TreeManager.nodeStates[node.path] = not TreeManager.nodeStates[node.path]
            refreshData()
            v.invalidate()
          end
          break
        end
        accumY = accumY + node.height
      end
     else
      velocityTracker.computeCurrentVelocity(1000)
      scroller.fling(TreeManager.scrollX, TreeManager.scrollY,
      -velocityTracker.getXVelocity(), -velocityTracker.getYVelocity(),
      0, TreeManager.maxScrollX, 0, TreeManager.maxScrollY)
      v.postOnAnimation(computeScroll)
    end
  end
  return true
end

refreshData()
