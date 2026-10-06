# 上游与发布边界

本次本地集成依赖 [MCRcortex/voxy](https://github.com/MCRcortex/voxy)，固定 `263` 提交 `2c5698e640ae8e42f1be8385f54f78911e2d235c`。

该提交的 LICENSE.md 为 Copyright 2025 MCRcortex、All rights reserved、Do not redistribute。上游代码、修改后的上游文件、上游 Git 历史及包含它们的完整 JAR 均未发布到本仓库。MIT 仅适用于本仓库提供的独立新增实现，不重新许可 Voxy 或 Minecraft。

本仓库提供新增 Vulkan 渲染、数据预算工具、导入辅助类、测试和说明；包名和接口用于对接上游，相关第三方依赖不随仓库分发。现阶段这是源码组件仓库，不能单独构建成完整 Minecraft 模组。完整再分发需要另行获得上游许可。
