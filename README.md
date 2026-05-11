# Play Queue Example

这是一个 Android/Kotlin 播放队列示例项目，用来演示如何在超大规模歌单场景下实现播放队列、分页加载、去重、预加载和播放状态维护。

项目由两个模块组成：

- `play-queue`：可复用的播放队列核心库。
- `app`：基于 Compose、Room、Paging 和 Hilt 的示例应用。

## 核心功能

`play-queue` 模块负责播放队列的通用逻辑：

- 支持把歌单或单曲加入播放队列，并提供三种队列动作：
  - `PlayNow`：立即播放，替换当前播放队列并从目标内容开始播放。
  - `InsertNext`：插入到当前播放歌曲之后，作为下一首播放。
  - `AddToEnd`：添加到播放队列末尾。
- 支持按全局位置播放队列中的歌曲。
- 支持超大歌单分页加载，避免一次性把全部歌曲加载进内存。
- 支持播放列表 UI 窗口加载，只维护屏幕附近范围的数据。
- 支持上一首、下一首、顺序播放、列表循环、单曲循环和随机播放。
- 支持提前准备上一首和下一首。
- 支持重复歌曲去重。
- 支持加载失败页、占位行、坏数据行等播放队列 UI 状态。

`app` 模块负责展示核心库的使用方式：

- 使用 Room 保存模拟歌单、歌曲和播放队列数据。
- 使用 Paging 显示歌单和歌单歌曲。
- 使用 Jetpack Compose 构建界面。
- 使用 Hilt 注入 Repository、Dao 和播放控制器。
- 提供底部播放控制条。
- 提供播放队列页面，展示队列歌曲、加载状态、占位行和错误行。
- `PlayQueueMusicApi` 是模拟接口，不是真实网络请求；它从本地 source 表中分页读取歌曲。

## 数据流

整体数据流如下：

```text
Compose UI
  -> ViewModel
  -> PlaybackQueueController
  -> PlayQueueRepository
  -> Room Dao / Mock Api
  -> RepositorySnapshot
  -> UI Rows
```

## 项目重点

这个项目的重点不是完整的真实音频播放能力。当前 `PlaybackQueuePlayerDelegate` 主要是空实现，用来预留接入真实播放器的位置。

项目真正关注的是播放队列本身：

- 如何管理大规模播放队列。
- 如何根据可见窗口分页加载数据。
- 如何在播放过程中准备上一首和下一首。
- 如何处理重复歌曲。
- 如何把 Repository 快照映射成 UI 可展示的行。
- 如何避免超大歌单导致内存压力。

## 技术栈

- Kotlin
- Android Gradle Plugin
- Jetpack Compose
- Room
- Paging
- Hilt
- Kotlin Coroutines / Flow
