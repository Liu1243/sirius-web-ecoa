# ECOADT.genmodel 代码生成指南

## 概述

ECOADT.genmodel 是一个 EMF (Eclipse Modeling Framework) GenModel 文件，用于从 ECOA (European Component Oriented Architecture) 数据类型的 XSD 模式文件生成 Java 代码。

## 什么是 GenModel？

GenModel 是 EMF 的代码生成配置文件，它：
- 定义了如何从 Ecore 模型生成 Java 代码
- 包含代码生成的各种设置（包名、目录、编译级别等）
- 引用源 XSD 模式文件
- 配置生成的代码结构

## ECOADT.genmodel 包含的内容

该 GenModel 文件配置了从以下 ECOA XSD 模式生成代码：

### 核心模式
- `ecoa-types-2.0.xsd` - ECOA 类型定义
- `ecoa-interface-2.0.xsd` - 接口定义
- `ecoa-implementation-2.0.xsd` - 实现定义
- `ecoa-deployment-2.0.xsd` - 部署配置
- `ecoa-project-2.0.xsd` - 项目结构

### SCA 相关模式
- `sca-*.xsd` - Service Component Architecture 定义
- `ecoa-sca-*.xsd` - ECOA SCA 扩展

### 其他模式
- 插入策略、模块行为、UDP 绑定等

## 代码生成方法

### 方法 1: 使用 Maven（推荐）

这是最简单和推荐的方法，已经在 pom.xml 中配置好。

#### 步骤：

1. **进入项目目录**
   ```bash
   cd packages/ecoa/backend/sirius-components-ecoa
   ```

2. **运行 Maven 生成代码**
   ```bash
   mvn clean generate-sources
   ```

3. **生成的代码位置**
   ```
   target/generated-sources/ecoa/
   ```

#### Maven 构建过程说明：

1. **编译代码生成器** (`generate-sources` 阶段)
   - 编译 `src/codegen/java/com/dassault/ecoa/codegen/EmfGenRunner.java`
   - 输出到 `target/codegen-classes/`

2. **运行代码生成器** (`generate-sources` 阶段)
   - 执行 `EmfGenRunner` 主类
   - 读取 `ECOADT.genmodel`
   - 生成 Java 源代码到 `target/generated-sources/ecoa/`

3. **添加生成的源代码** (`generate-sources` 阶段)
   - 将生成的代码添加到编译路径
   - Maven 会自动编译这些生成的源文件

### 方法 2: 完整构建

如果你想构建整个模块（包括生成代码和编译）：

```bash
cd packages/ecoa/backend/sirius-components-ecoa
mvn clean install
```

### 方法 3: 从父 POM 构建

从 ecoa backend 根目录构建所有模块：

```bash
cd packages/ecoa/backend
mvn clean install
```

这将构建：
- `sirius-components-ecoa` - 主模型代码
- `sirius-components-ecoa-edit` - 编辑支持代码

### 方法 4: 使用 Eclipse IDE

如果你使用 Eclipse IDE：

1. 右键点击 `ECOADT.genmodel` 文件
2. 选择 "Generate Model Code"
3. 代码将生成到配置的目录

### 方法 5: 手动运行代码生成器

如果需要手动运行（用于调试）：

```bash
# 首先编译代码生成器
cd packages/ecoa/backend/sirius-components-ecoa
mvn clean compile -Dexec.skip=true

# 然后手动运行
java -cp "target/codegen-classes:$HOME/.m2/repository/org/eclipse/emf/org.eclipse.emf.codegen.ecore/2.38.0/org.eclipse.emf.codegen.ecore-2.38.0.jar:..." \
  com.dassault.ecoa.codegen.EmfGenRunner \
  src/main/resources/model/ECOADT.genmodel \
  target/generated-sources/ecoa
```

## 生成的代码结构

生成的代码将包含以下包结构：

```
target/generated-sources/ecoa/
├── org/w3/_2001/xml/schema/          # XML Schema 类型
├── technology/ecoa/
│   ├── types/                         # ECOA 类型
│   ├── interface_/                    # 接口定义
│   ├── implementation/                # 实现
│   ├── deployment/                    # 部署
│   ├── project/                       # 项目
│   ├── logicalsystem/                 # 逻辑系统
│   ├── cross/platforms/view/          # 跨平台视图
│   ├── insertion/policy/              # 插入策略
│   ├── module/behaviour/              # 模块行为
│   ├── udpbinding/                    # UDP 绑定
│   ├── bin/desc/                      # 二进制描述
│   ├── uid/                           # UID 映射
│   └── sca/extension/                 # SCA 扩展
└── org/open/oasis/docs/ns/opencsa/sca/ # SCA 核心
```

每个包通常包含：
- **接口类** - 定义模型元素的接口
- **实现类** - 实现类（通常以 `Impl` 结尾）
- **工厂类** - 创建模型元素的工厂
- **包类** - 包元数据和注册
- **适配器工厂** - 用于编辑支持

## 验证代码生成

生成代码后，你可以验证：

1. **检查生成的文件**
   ```bash
   ls -la target/generated-sources/ecoa/
   ```

2. **查看生成的包**
   ```bash
   find target/generated-sources/ecoa -name "*.java" | head -20
   ```

3. **编译验证**
   ```bash
   mvn compile
   ```

## 常见问题

### 问题 1: 代码生成器类找不到

**错误信息:**
```
Error: Could not find or load main class com.dassault.ecoa.codegen.EmfGenRunner
```

**解决方案:**
确保 `EmfGenRunner.java` 文件存在于正确的位置：
```
src/codegen/java/com/dassault/ecoa/codegen/EmfGenRunner.java
```

### 问题 2: GenModel 文件找不到

**错误信息:**
```
ERROR: Could not load genmodel from: ...
```

**解决方案:**
检查 GenModel 文件路径：
```bash
ls -la src/main/resources/model/ECOADT.genmodel
```

### 问题 3: XSD 模式文件找不到

**错误信息:**
```
Cannot resolve reference to schema file
```

**解决方案:**
确保所有 XSD 文件存在于 `src/main/resources/schema/` 目录中。

### 问题 4: 生成的代码编译错误

**解决方案:**
1. 清理并重新生成：
   ```bash
   mvn clean generate-sources
   ```

2. 检查 EMF 依赖版本是否匹配

3. 确保 Java 版本为 17 或更高

## GenModel 配置说明

ECOADT.genmodel 的关键配置：

```xml
<genmodel:GenModel
  modelDirectory="/sirius-components-ecoa/src/main/java"
  editDirectory="/sirius-components-ecoa-edit/src/main/java"
  modelPluginID="sirius-components-ecoa"
  complianceLevel="17.0"
  ...>
```

- **modelDirectory**: 生成模型代码的目录（在 Maven 构建时会被覆盖）
- **editDirectory**: 生成编辑代码的目录
- **complianceLevel**: Java 编译级别（17.0 = Java 17）
- **importerID**: 使用 XSD 导入器

## 高级用法

### 仅生成特定包

如果你只想生成特定的包，可以修改 `EmfGenRunner.java` 来过滤包：

```java
for (GenPackage genPackage : genModel.getGenPackages()) {
    if (genPackage.getPrefix().equals("typ")) {  // 只生成 types 包
        generator.generate(genPackage, ...);
    }
}
```

### 自定义输出目录

在 pom.xml 中修改输出目录参数：

```xml
<arguments>
    <argument>${project.basedir}/src/main/resources/model/ECOADT.genmodel</argument>
    <argument>${project.build.directory}/custom-output</argument>
</arguments>
```

### 生成编辑器代码

要生成编辑器支持代码，需要在 `EmfGenRunner.java` 中添加：

```java
generator.generate(
    genModel,
    GenBaseGeneratorAdapter.EDIT_PROJECT_TYPE,
    new BasicMonitor.Printing(System.out)
);
```

## 集成到 CI/CD

在 CI/CD 管道中，代码生成会自动执行：

```yaml
# GitHub Actions 示例
- name: Generate ECOA Model Code
  run: |
    cd packages/ecoa/backend/sirius-components-ecoa
    mvn clean generate-sources
    
- name: Build ECOA Module
  run: |
    cd packages/ecoa/backend
    mvn clean install
```

## 参考资源

- [EMF Documentation](https://www.eclipse.org/modeling/emf/)
- [EMF GenModel Guide](https://wiki.eclipse.org/EMF/FAQ#GenModel)
- [ECOA Standard](https://www.ecoa.technology/)

## 总结

使用 Maven 生成代码是最简单的方法：

```bash
cd packages/ecoa/backend/sirius-components-ecoa
mvn clean generate-sources
```

生成的代码将位于 `target/generated-sources/ecoa/` 目录中，并自动包含在编译路径中。
