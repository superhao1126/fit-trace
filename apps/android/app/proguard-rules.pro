# Retrofit 和 Kotlin Serialization 使用各自随库附带的 consumer rules。
# 新增反射入口时按具体类型补充，禁止整包 keep。
# 第三方验证码只通过这些显式标注的方法回传一次性凭据。
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
