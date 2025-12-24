package com.yifeng.autogml.action

import android.util.Log

sealed class Action {
    data class Tap(val x: Int, val y: Int) : Action()
    data class DoubleTap(val x: Int, val y: Int) : Action()
    data class LongPress(val x: Int, val y: Int) : Action()
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int) : Action()
    data class Type(val text: String) : Action()
    data class Launch(val appName: String) : Action()
    object Back : Action()
    object Home : Action()
    data class Wait(val durationMs: Long) : Action()
    data class Finish(val message: String) : Action()
    data class Error(val reason: String) : Action()
    object Unknown : Action()
}

object ActionParser {
    
    fun parse(response: String, screenWidth: Int, screenHeight: Int): Action {
        val cleanResponse = response.trim()
        Log.d("ActionParser", "Parsing: $cleanResponse")

        // 1. Try to match finish(message="...")
        val finishRegex = Regex("""finish\s*\(\s*message\s*=\s*["'](.*?)["']\s*\)""", RegexOption.IGNORE_CASE)
        finishRegex.find(cleanResponse)?.let {
            return Action.Finish(it.groupValues[1])
        }

        // 2. Try to match do(action="...", ...)
        // We capture the content inside do(...)
        val doRegex = Regex("""do\s*\((.*)\)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val doMatch = doRegex.find(cleanResponse)
        
        if (doMatch != null) {
            val args = doMatch.groupValues[1]
            val params = parseParams(args)
            
            val actionType = params["action"]?.toString() ?: return Action.Error("Missing action type")
            
            return when (actionType.lowercase()) {
                "tap" -> {
                    val element = params["element"] as? List<*>
                    parseTapAction(element, screenWidth, screenHeight) { x, y -> Action.Tap(x, y) }
                }
                "double tap" -> {
                    val element = params["element"] as? List<*>
                    parseTapAction(element, screenWidth, screenHeight) { x, y -> Action.DoubleTap(x, y) }
                }
                "long press" -> {
                    val element = params["element"] as? List<*>
                    parseTapAction(element, screenWidth, screenHeight) { x, y -> Action.LongPress(x, y) }
                }
                "swipe" -> {
                    val start = params["start"] as? List<*>
                    val end = params["end"] as? List<*>
                    if (start != null && end != null && start.size >= 2 && end.size >= 2) {
                        val sx = (start[0] as Number).toFloat()
                        val sy = (start[1] as Number).toFloat()
                        val ex = (end[0] as Number).toFloat()
                        val ey = (end[1] as Number).toFloat()
                        Action.Swipe(
                            (sx / 1000f * screenWidth).toInt(),
                            (sy / 1000f * screenHeight).toInt(),
                            (ex / 1000f * screenWidth).toInt(),
                            (ey / 1000f * screenHeight).toInt()
                        )
                    } else {
                        Action.Error("Invalid coordinates for Swipe")
                    }
                }
                "type" -> {
                    val text = params["text"]?.toString() ?: return Action.Error("Missing text for Type")
                    Action.Type(text)
                }
                "launch" -> {
                    val app = params["app"]?.toString() ?: return Action.Error("Missing app name for Launch")
                    Action.Launch(app)
                }
                "back" -> Action.Back
                "home" -> Action.Home
                "wait" -> {
                    val durationStr = params["duration"]?.toString() ?: "1"
                    val durationSeconds = durationStr.replace("seconds", "").trim().toDoubleOrNull() ?: 1.0
                    Action.Wait((durationSeconds * 1000).toLong())
                }
                else -> Action.Unknown
            }
        }
        
        // 3. Fallback: If it's not a known command, treat it as a conversational finish (matching Python logic)
        // Python: parse_action raises ValueError -> finish(message=response)
        return Action.Finish(cleanResponse)
    }

    private fun parseTapAction(element: List<*>?, screenWidth: Int, screenHeight: Int, factory: (Int, Int) -> Action): Action {
        if (element != null && element.size >= 2) {
            val relX = (element[0] as Number).toFloat()
            val relY = (element[1] as Number).toFloat()
            val absX = (relX / 1000f * screenWidth).toInt()
            val absY = (relY / 1000f * screenHeight).toInt()
            
            Log.d("ActionParser", "Action Analysis - Screen: ${screenWidth}x${screenHeight}")
            Log.d("ActionParser", "Action Analysis - Relative: ($relX, $relY)")
            Log.d("ActionParser", "Action Analysis - Absolute: ($absX, $absY)")
            
            return factory(absX, absY)
        } else if (element != null && element.size == 1) {
                // Some responses might use a single integer index? Unlikely for AutoGLM but just in case
                return Action.Error("Invalid element format: $element")
        } else {
            // Fallback: Check if it's a "box" format [y1, x1, y2, x2]
            if (element != null && element.size == 4) {
                    // Box format [y1, x1, y2, x2] -> click center
                val y1 = (element[0] as Number).toFloat()
                val x1 = (element[1] as Number).toFloat()
                val y2 = (element[2] as Number).toFloat()
                val x2 = (element[3] as Number).toFloat()
                
                val centerX = (x1 + x2) / 2
                val centerY = (y1 + y2) / 2
                
                return factory(
                    (centerX / 1000f * screenWidth).toInt(),
                    (centerY / 1000f * screenHeight).toInt()
                )
            } else {
                return Action.Error("Invalid element for action")
            }
        }
    }
    
    // Helper to parse response into thinking and action strings for history/logging
    fun parseResponseParts(content: String): Pair<String, String> {
        // Rule 1: Check for finish(message=
        if (content.contains("finish(message=")) {
            val parts = content.split("finish(message=", limit = 2)
            val thinking = parts[0].trim()
            val action = "finish(message=" + parts[1]
            return Pair(thinking, action)
        }

        // Rule 2: Check for do(action=
        if (content.contains("do(action=")) {
            val parts = content.split("do(action=", limit = 2)
            val thinking = parts[0].trim()
            val action = "do(action=" + parts[1]
            return Pair(thinking, action)
        }

        // Rule 3: Fallback to legacy XML tag parsing
        if (content.contains("<answer>")) {
            val parts = content.split("<answer>", limit = 2)
            val thinking = parts[0].replace("<think>", "").replace("</think>", "").trim()
            val action = parts[1].replace("</answer>", "").trim()
            return Pair(thinking, action)
        }

        // Rule 4: No markers found, return content as action
        return Pair("", content)
    }

    private fun parseParams(args: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        // Regex to match key="value" or key='value' or key=[1,2]
        // key = "value"
        val stringParam = Regex("""(\w+)\s*=\s*["'](.*?)["']""")
        stringParam.findAll(args).forEach {
            result[it.groupValues[1]] = it.groupValues[2]
        }
        
        // key = [123, 456] or key = (123, 456)
        val listParam = Regex("""(\w+)\s*=\s*[\[\(]([\d\s,.]+)[\]\)]""")
        listParam.findAll(args).forEach { match ->
            val key = match.groupValues[1]
            val listStr = match.groupValues[2]
            val list = listStr.split(",").mapNotNull { it.trim().toFloatOrNull() }
            result[key] = list
        }
        
        return result
    }
}
