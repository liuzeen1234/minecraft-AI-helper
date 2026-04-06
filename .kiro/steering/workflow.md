---
inclusion: always
---

# 工作流规则

- 不要自动执行 git commit，除非用户明确要求提交。修改完代码后留给用户检查。
- 修改 `helloworld.properties` 文件之前，必须先和用户确认修改内容，得到同意后再执行。
- 当用户要求"记住"某条规则或偏好时，直接写入 `.kiro/steering/` 下的 steering 文件，不要只是口头答应。
