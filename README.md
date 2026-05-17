# Gold Clock

Gold Clock 是一个原生 Android 应用，用于查看实时黄金价格，并按用户设置的多个阈值发出铃声通知。

## 产品设计

- 首页展示两种常用价格：人民币/克、美元/金衡盎司。
- 首页展示黄金期货近 3 个月日 K 线。
- 支持手动刷新，也支持开启后台监测。
- 用户可以添加多个预警，每个预警包含：
  - 方向：上涨到或高于、下跌到或低于
  - 单位：人民币/克、美元/金衡盎司
  - 阈值：任意正数
  - 开关：可单独启用或停用
- 预警只在价格首次进入触发区间时通知，价格离开区间后会重新布防，避免每次轮询重复响铃。
- 添加预警后会自动开启后台监测；“测试闹钟提醒”按钮可用于检查通知权限和铃声渠道。

## 技术实现

- 原生 Android Java，无第三方运行时依赖。
- `GoldPriceRepository` 拉取黄金价格和 USD/CNY 汇率。
- `KLineChartView` 自绘日 K 蜡烛图，不依赖第三方图表库。
- `AlertStorage` 使用 `SharedPreferences` 保存预警和监测开关。
- `PriceMonitorService` 使用前台服务每 60 秒刷新一次，并用闹钟铃声音频属性的高优先级通知渠道发出预警。
- Android 13+ 会请求 `POST_NOTIFICATIONS` 权限。

## 数据源

当前优先接入 [Convertz API](https://convertz.app/api-docs)，并内置两个免 key 备用源：

- `GET https://convertz.app/api/metals` 获取 XAU 美元/金衡盎司价格，兼容 `prices.XAU.USD` 和旧版 `XAU.price` 返回结构。
- `GET https://api.gold-api.com/price/XAU` 作为实时金价备用源。
- `GET https://www.vang.today/api/prices?type=XAUUSD` 作为实时金价备用源。
- `GET https://convertz.app/api/currency` 获取 USD/CNY 汇率。
- `GET https://open.er-api.com/v6/latest/USD` 作为 USD/CNY 汇率备用源。
- `GET https://query1.finance.yahoo.com/v8/finance/chart/GC=F?range=3mo&interval=1d` 获取黄金期货日 K OHLC 数据。
- Yahoo Finance 被限流时，会用 Vang Today 30 日历史价生成近似日 K 作为备用显示。
- 应用内按 `美元/盎司 * USD/CNY / 31.1034768` 计算人民币/克。

生产发布前建议把数据源抽到自己的后端代理，便于切换供应商、做缓存、监控失败率，并避免移动端直接依赖单一免费 API。

## 构建运行

用 Android Studio 打开本目录，等待 Gradle 同步，然后运行 `app`。

命令行构建需要本机安装 Android SDK 和 JDK 17：

```bash
./gradlew assembleDebug
```
