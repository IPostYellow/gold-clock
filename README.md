# Gold Clock

Gold Clock 是一个原生 Android 应用，用于查看实时黄金价格，并按用户设置的多个阈值发出铃声通知。

## 产品设计

- 首页展示两种常用价格：人民币/克、美元/金衡盎司。
- 支持手动刷新，也支持开启后台监测。
- 用户可以添加多个预警，每个预警包含：
  - 方向：上涨到或高于、下跌到或低于
  - 单位：人民币/克、美元/金衡盎司
  - 阈值：任意正数
  - 开关：可单独启用或停用
- 预警只在价格首次进入触发区间时通知，价格离开区间后会重新布防，避免每次轮询重复响铃。

## 技术实现

- 原生 Android Java，无第三方运行时依赖。
- `GoldPriceRepository` 拉取黄金价格和 USD/CNY 汇率。
- `AlertStorage` 使用 `SharedPreferences` 保存预警和监测开关。
- `PriceMonitorService` 使用前台服务每 60 秒刷新一次，并用高优先级通知渠道发出铃声预警。
- Android 13+ 会请求 `POST_NOTIFICATIONS` 权限。

## 数据源

当前接入 [Convertz API](https://convertz.app/api-docs)：

- `GET https://convertz.app/api/metals` 获取 XAU 美元/金衡盎司价格。
- `GET https://convertz.app/api/currency` 获取 USD/CNY 汇率。
- 应用内按 `美元/盎司 * USD/CNY / 31.1034768` 计算人民币/克。

生产发布前建议把数据源抽到自己的后端代理，便于切换供应商、做缓存、监控失败率，并避免移动端直接依赖单一免费 API。

## 构建运行

用 Android Studio 打开本目录，等待 Gradle 同步，然后运行 `app`。

命令行构建需要本机安装 Android SDK、JDK 17 和 Gradle：

```bash
gradle assembleDebug
```

当前仓库没有提交 Gradle wrapper，因为本机没有全局 Gradle 或 Android SDK 可用于生成和验证 wrapper。
