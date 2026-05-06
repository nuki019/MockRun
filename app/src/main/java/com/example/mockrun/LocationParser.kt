package com.example.mockrun

/**
 * 负责解析坐标输入字符串。
 * 输入格式："纬度, 经度"，例如 "45.737196, 126.627842"
 */
class LocationParser {

    /**
     * 将一组文本解析为坐标列表；无法解析的条目会被跳过。
     */
    fun parse(vararg inputs: String): List<Pair<Double, Double>> {
        return inputs.mapNotNull { raw ->
            val parts = raw.split(",")
            if (parts.size == 2) {
                val lat = parts[0].trim().toDoubleOrNull()
                val lon = parts[1].trim().toDoubleOrNull()
                if (lat != null && lon != null) lat to lon else null
            } else null
        }
    }
}
