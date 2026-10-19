# 首页图标来源

Figma 文件 Ct4AworB8fYBPjpSVwWbRW，页面「10 · 核心页面完整状态」，画板 21:4957。
使用 Figma Plugin API exportAsync 原样导出 PNG；下载工具的临时链接返回了空正文，未使用该空文件。
原生本地资源位于 ../../res/drawable-nodpi/home_*.png，不依赖临时 URL。

| 资源 | 原图层 | 用途 |
| --- | --- | --- |
| body | 21:4994 | 身体快捷入口、身体记录 |
| meal | 21:4999 | 饮食快捷入口、饮食记录 |
| workout | 21:5004 | 训练快捷入口、训练记录 |
| photo | 21:5009 | 照片快捷入口、照片记录 |
| home | 21:5054 | 首页导航 |
| trends | 21:5057 | 趋势导航 |
| archive | 21:5060 | 档案导航 |
| profile | 21:5063 | 我的导航 |

卡片、圆角、分隔线和日期选中背景使用原生 Compose 布局，避免将设计中的背景矢量当成整页图片。


## 我的页面（2026-10-16）

主画板 21:5479，二级页 23:7544 / 23:7572 / 23:7605 / 23:7651 / 23:7762。
新增 PNG 仍由 Figma 文件内原图层 exportAsync 直接导出，未重画。

| 资源 | 原图层或来源 | 用途 |
| --- | --- | --- |
| profile_avatar | 21:5488 | 默认头像 |
| profile_archive | 21:5494 | 档案及协议入口 |
| profile_right | 21:5498 | 导航行箭头 |
| profile_back | 23:7548 | 二级页返回 |
| profile_brand | apps/h5/public/brand/app-icon-512.png | 关于页，复用 Figma 同源品牌素材 |

头像来自接口时使用 Coil AsyncImage；缺失、加载中或加载失败时保留设计默认头像。底部导航复用首页资源。
