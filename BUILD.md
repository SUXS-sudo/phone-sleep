# 构建

需要 Windows、JDK 17、Android SDK Platform 34 和 Build Tools 34.0.0。电脑端使用系统自带的 .NET Framework C# 编译器。

```powershell
.\build.ps1
# 或指定工具目录
.\build.ps1 -JdkHome 'C:\Path\To\jdk-17' -AndroidSdk 'C:\Path\To\Android\Sdk'
```

安装包和电脑程序生成在 `dist/`。临时文件和本地开发签名密钥保存在 `build/`，均不提交到仓库。默认签名只适用于本地开发；更新已有安装需要沿用相同密钥。

源码测试位于 `tests/`，包含通信校验、息屏快捷键冲突处理和手机授权流程。
