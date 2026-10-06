# Vulkan Voxy 新增源码

Minecraft 26.3 / Fabric / Java 25 的 Vulkan 远景渲染研发，目标为 Apple Silicon / MoltenVK、4 GiB Java 堆。

**这是本次独立新增组件的源码仓库，不能单独构建完整模组。** 上游 Voxy 禁止再分发，因此完整集成文件、Git 历史和安装 JAR 未公开。来源与许可边界见 [UPSTREAM.md](UPSTREAM.md)。本次新增代码使用 MIT 许可。

## 实现

- 共用游戏 Vulkan 设备与提交；分页紧凑四边形，GPU 解码、视锥/方向/保守 HiZ 剔除及批量间接绘制。
- GPU 生成实例化四边形流，每页最多三组绘制；设备不支持子组或工作流分配受限时使用回退路径。
- CPU 贴图烘焙输入、模型上传与网格构建快照分离；近远景覆盖、透明裁剪及水体。
- 字节预算、压缩冷缓存、有界任务与流式导入辅助类；GPU 计量包含在途和待回收分配。
- 简体中文设置、独立隐藏 QA、固定 `-Xmx4G`，不改变正式实例后端。

## 此轮修复

空区段和等待上传的同版本网格不再反复重建；无压力时保留细网格，视野外保留粗覆盖；低显存预算退出压力状态的阈值不再不可达。保存设置或切换世界时，旧 GPU 资源全部退休后再创建新渲染器，避免两份资源同时撑爆预算。细分失败时保留父级覆盖；有空间后恢复细分。

窗口尺寸变化且GPU预算不足时，HiZ延迟分配并保留正常绘制；失败的部分构造回收资源，旧缓冲继续按提交完成情况退休。

距离、分辨率和用户细节设置不随优化偷偷降低。细分不足或预算耗尽仍可能出现较粗 LOD，不能承诺任意距离和细节组合都可维持全精度。

## 集成与测试

完整本地集成还修改了上游生命周期、模型、区段摄入、缓存、存储和设置入口，参见 [集成说明](docs/INTEGRATION.md)。本仓库不提供绕过上游许可的自动复制包。测试脚本需要自行准备合法获取的完整集成构建和 Minecraft 运行库；不是首次安装器。

先设置 `JAVA_HOME` 为 Java 25。独立内存工具可直接编译验证，无需上游或游戏：

```sh
python3 scripts/test-primitives.py
```

完整集成的首次独立实例准备：

```sh
python3 scripts/prepare-qa.py --source-instance "/path/to/minecraft-instance" --world-path "/path/to/generated-world" --instance-dir build/qa-instance --seconds 120
python3 scripts/capture-run.py --name check --seconds 120 --no-import
```

脚本只写独立 QA 实例；不会生成未知远景。默认依赖从本机游戏实例读取，使用时应检查自己的实例路径和依赖。后台隐藏结果是离屏吞吐及阶段耗时，不能当作屏幕呈现 FPS。

当前版本修复测试及性能限制见 [验收记录](docs/VALIDATION.md)。不支持 Iris 光影包，不包含光影包引擎。
