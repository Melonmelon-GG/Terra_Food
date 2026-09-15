# Region landmark artwork

This directory contains decorative regional artwork for the culinary archive.
Artwork is selected in web/src/landmarks.ts; unknown regions and failed optional
images fall back to lungmen.png.

## Provincial-level artwork

The 34 images were generated with the built-in image tool on 2026-09-15, then
mechanically optimized to 1440-pixel-wide WebP files for the website.

| Region | Landmark | File |
| --- | --- | --- |
| 北京 | 天坛祈年殿 | beijing.webp |
| 天津 | 天津之眼与海河 | tianjin.webp |
| 河北 | 山海关 | hebei.webp |
| 山西 | 应县木塔 | shanxi.webp |
| 内蒙古 | 呼和浩特大召寺 | inner-mongolia.webp |
| 辽宁 | 沈阳故宫大政殿 | liaoning.webp |
| 吉林 | 长白山天池 | jilin.webp |
| 黑龙江 | 哈尔滨圣索菲亚教堂 | heilongjiang.webp |
| 上海 | 东方明珠与外滩 | shanghai.webp |
| 江苏 | 南京紫金山天文台 | jiangsu.webp |
| 浙江 | 西湖雷峰塔 | zhejiang.webp |
| 安徽 | 黄山 | anhui.webp |
| 福建 | 福建土楼 | fujian.webp |
| 江西 | 滕王阁 | jiangxi.webp |
| 山东 | 泰山南天门 | shandong.webp |
| 河南 | 龙门石窟 | henan.webp |
| 湖北 | 黄鹤楼 | hubei.webp |
| 湖南 | 岳阳楼与洞庭湖 | hunan.webp |
| 广东 | 广州塔与珠江 | guangdong.webp |
| 广西 | 桂林象鼻山 | guangxi.webp |
| 海南 | 海口骑楼老街 | hainan.webp |
| 重庆 | 洪崖洞 | chongqing.webp |
| 四川 | 都江堰与安澜索桥 | sichuan.webp |
| 贵州 | 甲秀楼 | guizhou.webp |
| 云南 | 大理崇圣寺三塔 | yunnan.webp |
| 西藏 | 布达拉宫 | tibet.webp |
| 陕西 | 西安大雁塔 | shaanxi.webp |
| 甘肃 | 嘉峪关 | gansu.webp |
| 青海 | 塔尔寺 | qinghai.webp |
| 宁夏 | 西夏陵 | ningxia.webp |
| 新疆 | 吐鲁番苏公塔 | xinjiang.webp |
| 台湾 | 台北 101 | taiwan.webp |
| 香港 | 维多利亚港 | hong-kong.webp |
| 澳门 | 大三巴牌坊 | macau.webp |

## Shared generation prompt

Use case: stylized-concept. Asset type: responsive website decorative landmark
background for a Chinese culinary archive. Generate the named region's real,
recognizable landmark as a delicate Chinese ink-wash and pale-watercolor
architectural or landscape line drawing. Use refined pencil-like contours, warm
gray and restrained sepia on ivory rice paper. Compose as a wide 16:9 landscape:
the landmark occupies the right 52 percent while the left 48 percent fades into
quiet mist for page copy. Keep the mood airy, archival and low contrast. Include
no text, labels, UI, border, people, flags, logos, watermark, saturated colors,
heavy black ink, clutter or fictional structures.

lungmen.png is an AI-generated Lungmen-inspired placeholder rather than an
official game asset. Its prompt requested a pale sepia mountain city, pagoda
tower and elevated bridges on the right, fading into an ivory background on the
left, with no text, UI, food or characters.

Province lookup strips common suffixes including 省, 市, 自治区 and 特别行政区.
Decorative artwork must never change a dish's actual province, city, name or
other data. The backdrop is hidden from screen readers, and the share ticket
embeds the selected image before PNG export.
